#!/usr/bin/env python3
"""批次 2 冒烟验证：服务目录、价格预览、发布下架。

前提
----
应用已启动（local profile），且已执行 V1 + V2 + V3 迁移（V3 含服务目录种子数据）。

覆盖范围
--------
* 公共接口：分类树、服务项目列表与详情、价格预览（固定价 + 面积阶梯 + 加项）
* 拒绝路径：必选规格缺失、规格值不存在、服务项目不存在、未登录访问运营接口
* 运营接口（需要提供运营账号）：建草稿 → 未配标准不能发布 → 配标准 → 发布 → 下架 → 版本冲突

运营账号从哪来
--------------
系统没有预置管理员（docs/06 明确不种默认账号），所以运营相关检查默认跳过。
先用任意手机号注册，再在库里授予 OPERATOR 角色，然后用该账号跑本脚本：

    INSERT INTO account_role (account_id, role_code, status)
    SELECT id, 'OPERATOR', 'ACTIVE' FROM account WHERE phone = '<手机号>';

    python scripts/dev/smoke-batch2.py --operator-phone 13800000000 --operator-password Hestia2026

用法
----
    python scripts/dev/smoke-batch2.py
    python scripts/dev/smoke-batch2.py --base-url http://127.0.0.1:18080
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from decimal import Decimal

ITEM_FIXED = "SVC202610230001"
ITEM_AREA = "SVC202610230002"
CATEGORY_DAILY = "CAT202610230002"

PASSED: list[str] = []
FAILED: list[str] = []
SKIPPED: list[str] = []


class Failure(Exception):
    """单条检查失败。"""


class Api:
    """极简 HTTP 客户端：返回 (状态码, 响应体)，不抛 HTTP 错误。"""

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

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


def money(value) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.01"))


def expect_error(api: Api, method: str, path: str, expected_status: int,
                 expected_code: str, body: dict | None = None,
                 token: str | None = None) -> str:
    status, payload = api.call(method, path, body, token)
    expect(status == expected_status,
           f"期望 HTTP {expected_status}，实际 {status}（code={payload.get('code')}）")
    expect(payload.get("code") == expected_code,
           f"期望错误码 {expected_code}，实际 {payload.get('code')}")
    return f"{status}/{expected_code}"


def preview(api: Api, item_no: str, selections: list[dict] | None = None,
            quantity: float | None = None) -> dict:
    body: dict = {"selectedOptions": selections or []}
    if quantity is not None:
        body["quantity"] = quantity
    status, payload = api.call("POST", f"/api/v1/public/service-items/{item_no}/price-preview", body)
    expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
    return payload.get("data") or {}


def new_phone(prefix: str) -> str:
    return prefix + str(int(time.time() * 1000) % 100000000).zfill(8)


def main() -> int:
    parser = argparse.ArgumentParser(description="批次 2 服务目录冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--operator-phone", default=None,
                        help="已授予 OPERATOR 角色的手机号；不传则跳过运营接口检查")
    parser.add_argument("--operator-password", default="Hestia2026")
    args = parser.parse_args()

    api = Api(args.base_url)
    print(f"应用地址：{args.base_url}")
    print()

    state: dict[str, object] = {}

    # ---------------- 1. 公共目录 ----------------
    def category_tree() -> str:
        status, payload = api.call("GET", "/api/v1/public/service-categories")
        expect(status == 200, f"HTTP {status}")
        roots = payload.get("data") or []
        expect(len(roots) == 1, f"期望 1 个一级分类，实际 {len(roots)}")
        root = roots[0]
        expect(root.get("categoryName") == "居家保洁", f"一级分类名={root.get('categoryName')}")
        children = root.get("children") or []
        expect(len(children) == 3, f"期望 3 个二级分类，实际 {len(children)}")
        return f"root={root.get('categoryName')} children={len(children)}"

    def item_list() -> str:
        status, payload = api.call("GET", "/api/v1/public/service-items")
        expect(status == 200, f"HTTP {status}")
        items = payload.get("data") or []
        expect(len(items) == 2, f"期望 2 个已发布服务项目，实际 {len(items)}")
        return f"items={len(items)}"

    def item_list_by_category() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/public/service-items?categoryNo={CATEGORY_DAILY}")
        expect(status == 200, f"HTTP {status}")
        items = payload.get("data") or []
        expect(len(items) == 1, f"日常保洁分类下期望 1 个项目，实际 {len(items)}")
        expect(items[0].get("itemNo") == ITEM_FIXED, f"itemNo={items[0].get('itemNo')}")
        return f"itemNo={items[0].get('itemNo')}"

    def item_detail() -> str:
        status, payload = api.call("GET", f"/api/v1/public/service-items/{ITEM_FIXED}")
        expect(status == 200, f"HTTP {status}")
        data = payload.get("data") or {}
        options = data.get("options") or []
        standards = data.get("standards") or []
        expect(len(options) == 1, f"期望 1 个规格组，实际 {len(options)}")
        expect(len(options[0].get("values") or []) == 2,
               f"加项期望 2 个值，实际 {len(options[0].get('values') or [])}")
        expect(len(standards) == 6, f"期望 6 条标准检查项，实际 {len(standards)}")
        expect(any(s.get("evidenceRequired") for s in standards), "没有任何检查项要求照片证据")
        return f"options={len(options)} standards={len(standards)}"

    # ---------------- 2. 价格预览 ----------------
    def preview_fixed() -> str:
        data = preview(api, ITEM_FIXED)
        expect(money(data.get("payableAmount")) == Decimal("199.00"),
               f"应付={data.get('payableAmount')}")
        expect(data.get("durationMinutes") == 240, f"时长={data.get('durationMinutes')}")
        return f"应付={data.get('payableAmount')} 时长={data.get('durationMinutes')}"

    def preview_addons() -> str:
        data = preview(api, ITEM_FIXED, [
            {"optionCode": "ADDON", "valueCode": "FRIDGE"},
            {"optionCode": "ADDON", "valueCode": "WINDOW"},
        ])
        expect(money(data.get("addonAmount")) == Decimal("140.00"),
               f"加项={data.get('addonAmount')}")
        expect(money(data.get("payableAmount")) == Decimal("339.00"),
               f"应付={data.get('payableAmount')}")
        expect(data.get("durationMinutes") == 290, f"时长={data.get('durationMinutes')}")
        return f"加项={data.get('addonAmount')} 应付={data.get('payableAmount')} 时长={data.get('durationMinutes')}"

    def preview_area(value_code: str, expected: str) -> str:
        data = preview(api, ITEM_AREA, [{"optionCode": "AREA", "valueCode": value_code}])
        expect(money(data.get("payableAmount")) == Decimal(expected),
               f"{value_code} 应付={data.get('payableAmount')}，期望 {expected}")
        return f"{value_code} -> {data.get('payableAmount')}"

    def preview_missing_required() -> str:
        return expect_error(api, "POST", f"/api/v1/public/service-items/{ITEM_AREA}/price-preview",
                            400, "SERVICE_OPTION_REQUIRED", {"selectedOptions": []})

    def preview_unknown_value() -> str:
        return expect_error(api, "POST", f"/api/v1/public/service-items/{ITEM_AREA}/price-preview",
                            400, "SERVICE_OPTION_INVALID",
                            {"selectedOptions": [{"optionCode": "AREA", "valueCode": "AREA_999"}]})

    def preview_unknown_item() -> str:
        return expect_error(api, "POST", "/api/v1/public/service-items/SVC_NOT_EXIST/price-preview",
                            404, "SERVICE_ITEM_NOT_FOUND", {"selectedOptions": []})

    # ---------------- 3. 权限边界 ----------------
    def operator_requires_login() -> str:
        return expect_error(api, "GET", "/api/v1/operator/service-items", 401, "AUTH_REQUIRED")

    def customer_cannot_operate() -> str:
        phone = new_phone("137")
        status, payload = api.call("POST", "/api/v1/auth/customer/register",
                                   {"phone": phone, "password": "Hestia2026", "displayName": "目录冒烟"})
        expect(status == 200, f"注册失败 HTTP {status}")
        token = (payload.get("data") or {}).get("accessToken")
        expect(bool(token), "未取得令牌")
        return expect_error(api, "GET", "/api/v1/operator/service-items", 403,
                            "AUTH_FORBIDDEN", token=token)

    # ---------------- 4. 运营接口 ----------------
    def operator_token() -> str:
        status, payload = api.call("POST", "/api/v1/auth/login",
                                   {"phone": args.operator_phone, "password": args.operator_password})
        expect(status == 200, f"运营账号登录失败 HTTP {status} code={payload.get('code')}")
        token = (payload.get("data") or {}).get("accessToken")
        expect(bool(token), "未取得运营令牌")
        state["operatorToken"] = token
        return "已登录"

    def draft_item_body(name: str) -> dict:
        return {
            "categoryNo": CATEGORY_DAILY,
            "itemName": name,
            "shortDescription": "冒烟用临时服务项目",
            "serviceUnit": "ORDER",
            "pricingMode": "FIXED",
            "basePrice": 99.00,
            "durationMinutes": 60,
            "minQuantity": 1,
            "maxQuantity": 1,
            "options": [],
            "tiers": [],
        }

    def create_draft() -> str:
        token = state["operatorToken"]
        name = f"冒烟临时服务-{int(time.time() * 1000) % 1000000}"
        status, payload = api.call("POST", "/api/v1/operator/service-items",
                                   draft_item_body(name), token=token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload.get("data") or {}
        expect(data.get("status") == "DRAFT", f"status={data.get('status')}")
        state["draftNo"] = data.get("itemNo")
        return f"itemNo={data.get('itemNo')} status={data.get('status')}"

    def draft_not_publicly_visible() -> str:
        status, payload = api.call("GET", f"/api/v1/public/service-items/{state['draftNo']}")
        expect(status == 404, f"草稿不应出现在前台，实际 HTTP {status}")
        expect(payload.get("code") == "SERVICE_ITEM_NOT_FOUND", f"code={payload.get('code')}")
        return "草稿前台不可见"

    def publish_without_standards() -> str:
        return expect_error(api, "POST",
                            f"/api/v1/operator/service-items/{state['draftNo']}/publish",
                            409, "SERVICE_STANDARD_REQUIRED", token=state["operatorToken"])

    def set_standards() -> str:
        token = state["operatorToken"]
        body = {"items": [
            {"standardGroup": "服务前", "itemName": "服务前拍照", "required": True,
             "evidenceRequired": True, "sortOrder": 1},
            {"standardGroup": "清洁", "itemName": "地面清洁", "required": True,
             "evidenceRequired": False, "sortOrder": 2},
        ]}
        status, payload = api.call("PUT",
                                   f"/api/v1/operator/service-items/{state['draftNo']}/standards",
                                   body, token=token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        items = payload.get("data") or []
        expect(len(items) == 2, f"期望 2 条检查项，实际 {len(items)}")
        return f"standards={len(items)}"

    def publish() -> str:
        status, payload = api.call("POST",
                                   f"/api/v1/operator/service-items/{state['draftNo']}/publish",
                                   token=state["operatorToken"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload.get("data") or {}
        expect(data.get("status") == "PUBLISHED", f"status={data.get('status')}")
        state["publishedVersion"] = data.get("version")
        return f"status={data.get('status')} version={data.get('version')}"

    def published_is_visible() -> str:
        status, payload = api.call("GET", f"/api/v1/public/service-items/{state['draftNo']}")
        expect(status == 200, f"发布后前台应可见，实际 HTTP {status}")
        return "前台可见"

    def stale_version_rejected() -> str:
        body = draft_item_body("冒烟临时服务-改名")
        body["version"] = 0
        return expect_error(api, "PUT",
                            f"/api/v1/operator/service-items/{state['draftNo']}",
                            409, "SERVICE_ITEM_VERSION_CONFLICT", body,
                            token=state["operatorToken"])

    def offline() -> str:
        status, payload = api.call("POST",
                                   f"/api/v1/operator/service-items/{state['draftNo']}/offline",
                                   token=state["operatorToken"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload.get("data") or {}
        expect(data.get("status") == "OFFLINE", f"status={data.get('status')}")
        return f"status={data.get('status')}"

    def offline_hidden_from_public() -> str:
        status, payload = api.call("GET", f"/api/v1/public/service-items/{state['draftNo']}")
        expect(status == 404, f"下架后前台应 404，实际 HTTP {status}")
        expect(payload.get("code") == "SERVICE_ITEM_NOT_FOUND", f"code={payload.get('code')}")
        return "下架后前台不可见"

    def overlapping_tiers_rejected() -> str:
        token = state["operatorToken"]
        body = {
            "categoryNo": CATEGORY_DAILY,
            "itemName": "冒烟重叠阶梯",
            "serviceUnit": "SQUARE_METER",
            "pricingMode": "AREA_TIER",
            "basePrice": 0,
            "durationMinutes": 120,
            "minQuantity": 1,
            "maxQuantity": 1,
            "options": [{
                "optionCode": "AREA", "optionName": "面积", "selectionType": "SINGLE",
                "required": True, "minSelect": 1, "maxSelect": 1,
                "values": [{"valueCode": "A", "valueName": "小", "numericValue": 0}],
            }],
            "tiers": [
                {"tierType": "AREA_TIER", "minValue": 0, "maxValue": 60, "fixedPrice": 399},
                {"tierType": "AREA_TIER", "minValue": 50, "maxValue": 90, "fixedPrice": 499},
            ],
        }
        return expect_error(api, "POST", "/api/v1/operator/service-items",
                            409, "SERVICE_PRICE_TIER_CONFLICT", body, token=token)

    check("分类树：1 个一级 + 3 个二级", category_tree)
    check("服务项目列表：2 个已发布项目", item_list)
    check("按分类过滤服务项目", item_list_by_category)
    check("服务项目详情：规格组与 6 条标准", item_detail)
    check("价格预览·固定价 199 元", preview_fixed)
    check("价格预览·加项累加 140 元、时长 +50 分钟", preview_addons)
    check("价格预览·面积档 [0,60) 399 元", lambda: preview_area("AREA_0_60", "399.00"))
    check("价格预览·面积档 [60,90) 499 元", lambda: preview_area("AREA_60_90", "499.00"))
    check("价格预览·面积档 [90,120) 599 元", lambda: preview_area("AREA_90_120", "599.00"))
    check("价格预览·120 平米以上按单价 720 元", lambda: preview_area("AREA_120_PLUS", "720.00"))
    check("价格预览·必选规格缺失被拒绝", preview_missing_required)
    check("价格预览·规格值不存在被拒绝", preview_unknown_value)
    check("价格预览·服务项目不存在被拒绝", preview_unknown_item)
    check("未登录访问运营接口返回 401", operator_requires_login)
    check("消费者访问运营接口返回 403", customer_cannot_operate)

    if args.operator_phone:
        check("运营账号登录", operator_token)
        check("创建草稿服务项目", create_draft)
        check("草稿在前台不可见", draft_not_publicly_visible)
        check("未配标准不允许发布", publish_without_standards)
        check("配置服务标准", set_standards)
        check("发布服务项目", publish)
        check("发布后前台可见", published_is_visible)
        check("过期版本号被拒绝", stale_version_rejected)
        check("下架服务项目", offline)
        check("下架后前台不可见", offline_hidden_from_public)
        check("重叠价格阶梯被拒绝", overlapping_tiers_rejected)
    else:
        SKIPPED.append("运营接口（建草稿/发布/下架/版本冲突）——未提供 --operator-phone")
        print("[跳过] 运营接口检查：未提供 --operator-phone，见脚本头部说明")

    print()
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项，跳过 {len(SKIPPED)} 项。")
    if FAILED:
        print("失败清单：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("批次 2（服务目录）冒烟验证全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
