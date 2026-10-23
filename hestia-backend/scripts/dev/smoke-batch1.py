#!/usr/bin/env python3
"""批次 1 冒烟验证：注册、登录、令牌、地址、服务区域匹配、越权与角色隔离。

前提
----
应用已启动（local profile），且已执行 V1 + V2 迁移（含行政区与服务区域种子数据）。

与 smoke-test.ps1 的分工
------------------------
* ``smoke-test.ps1``   验证技术底座（MySQL / Redis / MinIO / RocketMQ 是否通）
* ``smoke-batch1.py``  验证业务闭环（identity + customer + location）

为什么用 Python 而不是 PowerShell
---------------------------------
只用标准库（urllib），在 Windows / macOS / Linux 上行为一致；
断言逻辑比 PowerShell 的 ``Invoke-RestMethod`` + 异常解析清晰得多。
项目里既有的 ``.ps1`` 脚本保留不动，本脚本是补充而非替代。

安全约定
--------
脚本只打印"令牌前 8 位 + 省略号"，绝不输出完整令牌与完整手机号，
避免把凭据写进终端历史或 CI 日志。

用法
----
    python scripts/dev/smoke-batch1.py
    python scripts/dev/smoke-batch1.py --base-url http://127.0.0.1:18080
"""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request

CITY_CODE = "440100"
DISTRICT_CODE = "440106"
STREET_WUSHAN = "440106001"
STREET_YUANCUN = "440106002"


class Failure(Exception):
    """单条检查失败。"""


class Api:
    """极简 HTTP 客户端：返回 (状态码, 响应体)，不抛 HTTP 错误。"""

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.token: str | None = None

    def call(self, method: str, path: str, body: dict | None = None,
             token: str | None = None) -> tuple[int, dict]:
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base_url + path, data=data,
                                         headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                payload = {"code": None, "message": raw[:200]}
            return error.code, payload


PASSED: list[str] = []
FAILED: list[str] = []


def check(name: str, func) -> None:
    try:
        detail = func()
        PASSED.append(name)
        print(f"[通过] {name}{('  ' + detail) if detail else ''}")
    except Failure as failure:
        FAILED.append(name)
        print(f"[失败] {name}  ->  {failure}")
    except Exception as error:  # noqa: BLE001 - 冒烟脚本需要报告任何异常
        FAILED.append(name)
        print(f"[失败] {name}  ->  {type(error).__name__}: {error}")


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise Failure(message)


def mask(token: str | None) -> str:
    if not token:
        return "<空>"
    return token[:8] + "…"


def expect_error(api: Api, method: str, path: str, expected_status: int,
                 expected_code: str, body: dict | None = None,
                 token: str | None = None) -> str:
    """断言请求被拒绝：状态码与业务错误码都要对上。

    业务错误一律走 ``Result.code``，只断言状态码不够——
    401 既可能是"没带令牌"也可能是"令牌过期"，前端处理方式不同。
    """
    status, payload = api.call(method, path, body, token)
    expect(status == expected_status,
           f"期望 HTTP {expected_status}，实际 {status}（code={payload.get('code')}）")
    expect(payload.get("code") == expected_code,
           f"期望错误码 {expected_code}，实际 {payload.get('code')}")
    return f"{status}/{expected_code}"


def new_phone(prefix: str) -> str:
    """生成 11 位手机号，毫秒级唯一，保证脚本可重复执行。"""
    return prefix + str(int(time.time() * 1000) % 100000000).zfill(8)


def address_body(district: str, street: str, name: str, detail: str,
                 is_default: bool) -> dict:
    return {
        "label": "家",
        "contactName": name,
        "contactPhone": "13900139000",
        "provinceCode": "440000",
        "cityCode": CITY_CODE,
        "districtCode": district,
        "streetCode": street,
        "communityName": "华工教职工小区",
        "buildingNo": "3 栋",
        "unitNo": "2 单元",
        "floorNo": "6",
        "roomNo": "602",
        "detailAddress": detail,
        "isDefault": is_default,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="批次 1 业务闭环冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080",
                        help="应用地址，默认 http://127.0.0.1:8080")
    parser.add_argument("--password", default="Hestia2026",
                        help="冒烟账号密码（仅本地验证，勿用于真实环境）")
    args = parser.parse_args()

    api = Api(args.base_url)
    password = args.password
    phone_a = new_phone("138")
    phone_b = new_phone("139")
    print(f"应用地址：{args.base_url}")
    print(f"本次冒烟账号：{phone_a[:3]}****{phone_a[-4:]} / {phone_b[:3]}****{phone_b[-4:]}")
    print()

    state: dict[str, object] = {}

    # ---------------- 1. 公共接口：不登录也要能查片区、能匹配 ----------------
    def public_areas() -> str:
        status, payload = api.call("GET", f"/api/v1/public/service-areas?cityCode={CITY_CODE}")
        expect(status == 200, f"HTTP {status}")
        expect(payload.get("code") == "OK", f"code={payload.get('code')}")
        areas = payload.get("data") or []
        expect(len(areas) >= 2, f"期望至少 2 个片区，实际 {len(areas)}")
        return f"片区数={len(areas)}"

    def match_wushan() -> str:
        status, payload = api.call("POST", "/api/v1/public/service-area-match", {
            "cityCode": CITY_CODE, "districtCode": DISTRICT_CODE, "streetCode": STREET_WUSHAN})
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        expect(data.get("matchStatus") == "MATCHED", f"matchStatus={data.get('matchStatus')}")
        expect(data.get("matchLevel") == "STREET",
               f"五山街道应命中 STREET 级片区，实际 {data.get('matchLevel')}")
        state["streetAreaId"] = data.get("serviceAreaId")
        return f"area={data.get('areaName')} level={data.get('matchLevel')}"

    def match_yuancun() -> str:
        status, payload = api.call("POST", "/api/v1/public/service-area-match", {
            "cityCode": CITY_CODE, "districtCode": DISTRICT_CODE, "streetCode": STREET_YUANCUN})
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        expect(data.get("matchStatus") == "MATCHED", f"matchStatus={data.get('matchStatus')}")
        expect(data.get("matchLevel") == "DISTRICT",
               f"员村街道只被区县级片区覆盖，应命中 DISTRICT，实际 {data.get('matchLevel')}")
        state["districtAreaId"] = data.get("serviceAreaId")
        return f"area={data.get('areaName')} level={data.get('matchLevel')}"

    # ---------------- 2. 认证 ----------------
    def anonymous_me() -> str:
        return expect_error(api, "GET", "/api/v1/auth/me", 401, "AUTH_REQUIRED")

    def register_a() -> str:
        status, payload = api.call("POST", "/api/v1/auth/customer/register", {
            "phone": phone_a, "password": password, "displayName": "冒烟客户A"})
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload.get("data") or {}
        expect(bool(data.get("accessToken")), "未返回 accessToken")
        account = data.get("account") or {}
        expect(account.get("activeRole") == "CUSTOMER", f"activeRole={account.get('activeRole')}")
        state["tokenA"] = data["accessToken"]
        state["accountNoA"] = account.get("accountNo")
        return f"accountNo={account.get('accountNo')} phone={account.get('phoneMasked')}"

    def duplicate_register() -> str:
        return expect_error(api, "POST", "/api/v1/auth/customer/register",
                            409, "AUTH_PHONE_ALREADY_EXISTS",
                            {"phone": phone_a, "password": password, "displayName": "冒烟客户A"})

    def login_a() -> str:
        status, payload = api.call("POST", "/api/v1/auth/login",
                                   {"phone": phone_a, "password": password})
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        expect(bool(data.get("accessToken")), "未返回 accessToken")
        state["tokenA"] = data["accessToken"]
        return f"expiresIn={data.get('expiresIn')}s token={mask(data['accessToken'])}"

    def wrong_password() -> str:
        return expect_error(api, "POST", "/api/v1/auth/login", 401, "AUTH_INVALID_CREDENTIALS",
                            {"phone": phone_a, "password": "WrongPass123"})

    def me() -> str:
        status, payload = api.call("GET", "/api/v1/auth/me", token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        expect(data.get("activeRole") == "CUSTOMER", f"activeRole={data.get('activeRole')}")
        expect("*" in (data.get("phoneMasked") or ""), f"手机号未脱敏：{data.get('phoneMasked')}")
        return f"role={data.get('activeRole')} phone={data.get('phoneMasked')}"

    def customer_hits_admin() -> str:
        return expect_error(api, "GET", "/api/v1/admin/accounts", 403, "AUTH_FORBIDDEN",
                            token=state["tokenA"])  # type: ignore[arg-type]

    def profile() -> str:
        status, payload = api.call("GET", "/api/v1/customer/profile", token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        expect(bool(data.get("customerNo")), "未返回 customerNo")
        expect(data.get("status") == "ACTIVE", f"status={data.get('status')}")
        return f"customerNo={data.get('customerNo')}"

    # ---------------- 3. 地址 ----------------
    def create_first_address() -> str:
        status, payload = api.call("POST", "/api/v1/customer/addresses",
                                   address_body(DISTRICT_CODE, STREET_WUSHAN, "周先生",
                                                "五山路 381 号", False),
                                   token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload.get("data") or {}
        expect(data.get("areaMatchStatus") == "MATCHED",
               f"areaMatchStatus={data.get('areaMatchStatus')}")
        expect(data.get("isDefault") is True,
               f"首条地址应自动成为默认，实际 isDefault={data.get('isDefault')}")
        expect(data.get("serviceAreaId") == state.get("streetAreaId"),
               f"命中的片区 {data.get('serviceAreaId')} 与公共匹配 {state.get('streetAreaId')} 不一致")
        state["addressNoA"] = data.get("addressNo")
        return f"addressNo={data.get('addressNo')} area={data.get('serviceAreaId')}"

    def create_second_address() -> str:
        status, payload = api.call("POST", "/api/v1/customer/addresses",
                                   address_body(DISTRICT_CODE, STREET_YUANCUN, "周女士",
                                                "员村二横路 12 号", False),
                                   token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload.get("data") or {}
        expect(data.get("isDefault") is not True, "第二条地址不应抢占默认")
        expect(data.get("serviceAreaId") == state.get("districtAreaId"),
               f"员村街道应命中片区 {state.get('districtAreaId')}，实际 {data.get('serviceAreaId')}")
        return f"addressNo={data.get('addressNo')} area={data.get('serviceAreaId')}"

    def list_addresses() -> str:
        status, payload = api.call("GET", "/api/v1/customer/addresses",
                                   token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status}")
        items = payload.get("data") or []
        expect(len(items) == 2, f"期望 2 条地址，实际 {len(items)}")
        defaults = [item for item in items if item.get("isDefault") is True]
        expect(len(defaults) == 1, f"期望恰好 1 条默认地址，实际 {len(defaults)}")
        return f"共 {len(items)} 条，默认 1 条"

    def snapshot() -> str:
        status, payload = api.call("GET",
                                   f"/api/v1/customer/addresses/{state['addressNoA']}/snapshot",
                                   token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload.get("data") or {}
        for field in ("provinceName", "cityName", "districtName", "streetName"):
            expect(bool(data.get(field)), f"快照缺少 {field}")
        return (f"{data.get('provinceName')}/{data.get('cityName')}"
                f"/{data.get('districtName')}/{data.get('streetName')}")

    def availability() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/customer/addresses/{state['addressNoA']}/availability-check",
            token=state["tokenA"])  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        expect(data.get("areaMatchStatus") == "MATCHED",
               f"areaMatchStatus={data.get('areaMatchStatus')}")
        return f"areaMatchStatus={data.get('areaMatchStatus')}"

    # ---------------- 4. 越权隔离 ----------------
    def register_b() -> str:
        status, payload = api.call("POST", "/api/v1/auth/customer/register", {
            "phone": phone_b, "password": password, "displayName": "冒烟客户B"})
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload.get("data") or {}
        expect(bool(data.get("accessToken")), "未返回 accessToken")
        state["tokenB"] = data["accessToken"]
        return f"accountNo={(data.get('account') or {}).get('accountNo')}"

    def cross_account_read() -> str:
        return expect_error(api, "GET", f"/api/v1/customer/addresses/{state['addressNoA']}",
                            404, "ADDRESS_NOT_FOUND", token=state["tokenB"])  # type: ignore[arg-type]

    # ---------------- 5. 改密后旧令牌立即失效 ----------------
    def change_password() -> str:
        old_token = state["tokenA"]
        status, payload = api.call("PUT", "/api/v1/auth/password",
                                   {"oldPassword": password, "newPassword": "Hestia2026New"},
                                   token=old_token)  # type: ignore[arg-type]
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["oldToken"] = old_token
        return "已修改"

    def old_token_rejected() -> str:
        return expect_error(api, "GET", "/api/v1/auth/me", 401, "AUTH_INVALID_TOKEN",
                            token=state["oldToken"])  # type: ignore[arg-type]

    check("公共片区列表 GET /api/v1/public/service-areas", public_areas)
    check("片区匹配·五山街道 命中 STREET 级片区", match_wushan)
    check("片区匹配·员村街道 命中 DISTRICT 级片区", match_yuancun)
    check("未带令牌访问 /auth/me 返回 401", anonymous_me)
    check("消费者注册", register_a)
    check("重复注册同一手机号返回 409", duplicate_register)
    check("密码登录", login_a)
    check("错误密码返回 401（不暴露账号是否存在）", wrong_password)
    check("当前账号 /auth/me", me)
    check("消费者访问管理端返回 403", customer_hits_admin)
    check("客户档案 /customer/profile", profile)
    check("新增首条地址自动默认并命中 STREET 片区", create_first_address)
    check("新增第二条地址不抢占默认", create_second_address)
    check("地址列表：两条中只有一条默认", list_addresses)
    check("下单地址快照带行政区名称", snapshot)
    check("地址可用性检查", availability)
    check("注册第二个账号（越权验证用）", register_b)
    check("账号 B 读取账号 A 的地址返回 404", cross_account_read)
    check("修改密码", change_password)
    check("改密后旧令牌返回 401", old_token_rejected)

    print()
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项。")
    if FAILED:
        print("失败清单：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("批次 1 冒烟验证全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
