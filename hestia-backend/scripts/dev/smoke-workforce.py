#!/usr/bin/env python3
"""批次 2 人员模块冒烟验证：开户、审核、技能、证书、区域、可派单资格。

验证的核心命题
--------------
**账号激活不等于可派单。** 一个人可以登录得好好的，但只要技能没审核、
健康证没交、区域没配，资格校验就必须返回 ``eligible=false`` 并给出全部原因。
这条链路用单测覆盖不了——单测不读种子、不跑事务、不校验角色联动。

前提
----
1. 应用已启动（local profile），V1~V3 迁移已执行；
2. 库里存在一个已授 ``OPERATOR`` 角色的账号（docs/06 不提供默认管理员）。
   若没有，先用下面的 SQL 建一个，再用 ``--operator-phone`` 指过来：

       INSERT INTO account_role (account_id, role_code, status)
       SELECT id, 'OPERATOR', 'ACTIVE' FROM account WHERE phone = '<手机号>';

   注意 ``RoleCombinationPolicy`` 不允许同一账号同时持有 CUSTOMER 与 OPERATOR，
   所以不能拿消费者账号直接追加角色。

安全约定
--------
只打印令牌前 8 位与脱敏手机号；激活凭证同样只打印前 8 位。
证书编号全程不打印——它是要加密入库的敏感字段。

用法
----
    python scripts/dev/smoke-workforce.py
    python scripts/dev/smoke-workforce.py --operator-phone 13900000001 --operator-password Hestia2026
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

DEFAULT_ITEM_NO = "SVC202610230001"
DEFAULT_AREA_ID = 2
OTHER_AREA_ID = 1


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


def mask_phone(phone: str) -> str:
    return phone[:3] + "****" + phone[-4:]


def expect_error(api: Api, method: str, path: str, expected_status: int,
                 expected_code: str, body: dict | None = None,
                 token: str | None = None) -> str:
    status, payload = api.call(method, path, body, token)
    expect(status == expected_status,
           f"期望 HTTP {expected_status}，实际 {status}（code={payload.get('code')}）")
    expect(payload.get("code") == expected_code,
           f"期望错误码 {expected_code}，实际 {payload.get('code')}")
    return f"{status}/{expected_code}"


def new_phone(prefix: str) -> str:
    """生成 11 位手机号，毫秒级唯一，保证脚本可重复执行。"""
    return prefix + str(int(time.time() * 1000) % 100000000).zfill(8)


def main() -> int:
    parser = argparse.ArgumentParser(description="批次 2 人员模块冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--operator-phone", default="13900000001")
    parser.add_argument("--operator-password", default="Hestia2026")
    parser.add_argument("--worker-password", default="Hestia2026")
    parser.add_argument("--service-item", default=DEFAULT_ITEM_NO)
    parser.add_argument("--service-area-id", type=int, default=DEFAULT_AREA_ID)
    args = parser.parse_args()

    api = Api(args.base_url)
    item_no = args.service_item
    area_id = args.service_area_id
    worker_phone = new_phone("135")

    print("=" * 78)
    print("批次 2 · 人员模块冒烟验证")
    print(f"接口地址：{args.base_url}")
    print(f"运营账号：{mask_phone(args.operator_phone)}")
    print(f"新人员手机号：{mask_phone(worker_phone)}")
    print("=" * 78)

    # ---------------------------------------------------------------- 前置：运营登录
    status, payload = api.call("POST", "/api/v1/auth/login", {
        "phone": args.operator_phone, "password": args.operator_password})
    if status != 200:
        print(f"[中止] 运营账号登录失败：HTTP {status} code={payload.get('code')}")
        print("       请确认该账号存在、已激活、且已授 OPERATOR 角色：")
        print("       INSERT INTO account_role (account_id, role_code, status)")
        print("       SELECT id, 'OPERATOR', 'ACTIVE' FROM account WHERE phone = '<手机号>';")
        return 2
    operator_token = payload["data"]["accessToken"]
    print(f"运营令牌：{mask(operator_token)}\n")

    state: dict = {}

    # ---------------------------------------------------------------- 1. 创建人员
    def create_worker() -> str:
        status, payload = api.call("POST", "/api/v1/operator/workers", {
            "countryCode": "+86",
            "phone": worker_phone,
            "displayName": "冒烟人员",
            "realName": "冒烟测试员",
            "gender": "FEMALE",
            "employmentType": "FULL_TIME",
            "experienceYears": 3.5,
            "introduction": "冒烟脚本创建",
            "maxDailyOrders": 3,
        }, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        state["worker_no"] = data["workerNo"]
        state["activation_token"] = data["activationToken"]
        expect(data["workerNo"].startswith("WK"), "人员编号前缀应为 WK")
        expect(bool(data["activationToken"]), "新建人员必须返回一次性激活凭证")
        return f"workerNo={data['workerNo']} token={mask(data['activationToken'])}"

    check("创建人员档案并开户", create_worker)

    # ---------------------------------------------------------------- 2. 初始状态
    def initial_state() -> str:
        status, payload = api.call("GET", f"/api/v1/operator/workers/{state['worker_no']}",
                                   token=operator_token)
        expect(status == 200, f"HTTP {status}")
        data = payload["data"]
        expect(data["workStatus"] == "ONBOARDING", f"初始工作状态应为 ONBOARDING，实际 {data['workStatus']}")
        expect(data["auditStatus"] == "PENDING", f"初始审核状态应为 PENDING，实际 {data['auditStatus']}")
        expect(data["skills"] == [], "新人员不应有技能")
        expect(data["serviceAreas"] == [], "新人员不应有服务区域")
        state["account_no"] = data["accountNo"]
        return f"{data['workStatus']}/{data['auditStatus']} account={data['accountNo']}"

    check("新建人员处于待审核、未配置状态", initial_state)

    # ---------------------------------------------------------------- 3. 未激活即不可派单
    def eligibility_before_activation() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}&serviceAreaId={area_id}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        codes = {reason["code"] for reason in data["reasons"]}
        expect(data["eligible"] is False, "未激活人员不应可派单")
        expect("ACCOUNT_NOT_ACTIVE" in codes, f"应包含 ACCOUNT_NOT_ACTIVE，实际 {sorted(codes)}")
        expect("WORKER_AUDIT_NOT_APPROVED" in codes, "应包含 WORKER_AUDIT_NOT_APPROVED")
        expect("SKILL_NOT_VERIFIED" in codes, "应包含 SKILL_NOT_VERIFIED")
        expect("SERVICE_AREA_NOT_CONFIGURED" in codes, "应包含 SERVICE_AREA_NOT_CONFIGURED")
        return f"{len(codes)} 项原因"

    check("账号未激活时不可派单（原因齐全）", eligibility_before_activation)

    # ---------------------------------------------------------------- 4. 资质要求
    def set_qualification() -> str:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/service-items/{item_no}/qualification-requirements",
            {"requirements": [{"certificateType": "HEALTH", "required": True}]}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(len(data) == 1 and data[0]["certificateType"] == "HEALTH", f"实际 {data}")
        return "HEALTH 必填"

    check("配置服务项目资质要求（健康证必填）", set_qualification)

    # ---------------------------------------------------------------- 5. 激活账号
    def activate_worker() -> str:
        status, payload = api.call("POST", "/api/v1/auth/worker/activate", {
            "token": state["activation_token"], "password": args.worker_password})
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        state["worker_token"] = payload["data"]["accessToken"]
        expect(payload["data"]["account"]["roles"] and "WORKER" in payload["data"]["account"]["roles"],
               "激活后应持有 WORKER 角色")
        return f"token={mask(state['worker_token'])}"

    check("凭一次性凭证激活并设置密码", activate_worker)

    # ---------------------------------------------------------------- 6. 本人视角
    def worker_self_profile() -> str:
        status, payload = api.call("GET", "/api/v1/worker/profile", token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["workerNo"] == state["worker_no"], "本人档案应指向自己的编号")
        return f"{payload['data']['realName']}/{payload['data']['workStatus']}"

    check("服务人员可读取本人档案", worker_self_profile)

    # ---------------------------------------------------------------- 7. 提交证书
    def submit_certificate() -> str:
        status, payload = api.call("POST", "/api/v1/worker/certificates", {
            "certificateType": "HEALTH",
            "certificateNo": "JKZ-2026-" + state["worker_no"],
            "issuedAt": "2026-01-01",
            "expiresAt": "2027-01-01",
            "remark": "冒烟提交",
        }, state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        state["certificate_id"] = data["certificateId"]
        expect(data["auditStatus"] == "PENDING", f"本人提交的证书必须是待审核，实际 {data['auditStatus']}")
        expect(data["usable"] is False, "待审核证书不应可用")
        expect(data["usability"] == "PENDING_REVIEW", f"实际 {data['usability']}")
        return f"id={data['certificateId']} {data['usability']}"

    check("本人提交证书 -> 待审核、不可用", submit_certificate)

    # ---------------------------------------------------------------- 8. 证书未审不可派单
    def eligibility_pending_certificate() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}", token=operator_token)
        expect(status == 200, f"HTTP {status}")
        codes = {reason["code"] for reason in payload["data"]["reasons"]}
        expect("CERTIFICATE_EXPIRED" in codes,
               f"证书待审核应归入不可用原因，实际 {sorted(codes)}")
        expect("CERTIFICATE_REQUIRED" not in codes,
               "已经交了证书，不应报\"缺少证书\"")
        return "CERTIFICATE_EXPIRED"

    check("证书未审核 -> 记为证书不可用（而非缺少证书）", eligibility_pending_certificate)

    # ---------------------------------------------------------------- 9. 运营审核证书
    def review_certificate() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/workers/{state['worker_no']}"
                    f"/certificates/{state['certificate_id']}/review",
            {"auditStatus": "APPROVED", "remark": "冒烟审核通过"}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data["auditStatus"] == "APPROVED", f"实际 {data['auditStatus']}")
        expect(data["usable"] is True, "审核通过且未到期应可用")
        return data["usability"]

    check("运营审核证书 -> 变为可用", review_certificate)

    # ---------------------------------------------------------------- 10. 授权技能
    def replace_skills() -> str:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/workers/{state['worker_no']}/skills",
            {"skills": [{"serviceItemNo": item_no, "skillLevel": "INTERMEDIATE", "verified": True}]},
            operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        return item_no

    check("运营授权并审核技能", replace_skills)

    # ---------------------------------------------------------------- 11. 分配区域
    def replace_areas() -> str:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/workers/{state['worker_no']}/service-areas",
            {"serviceAreaIds": [area_id]}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        return f"area={area_id}"

    check("运营分配服务区域", replace_areas)

    # ---------------------------------------------------------------- 12. 归档区域不可分配
    def archived_area_rejected() -> str:
        # 用一个不存在的片区 ID 模拟"不可分配"，避免脚本去改库里的真实片区状态
        return expect_error(api, "PUT", f"/api/v1/operator/workers/{state['worker_no']}/service-areas",
                            400, "WORKER_SERVICE_AREA_INVALID",
                            {"serviceAreaIds": [999999]}, operator_token)

    check("不可用的服务区域被拒绝", archived_area_rejected)

    # ---------------------------------------------------------------- 13. 区域不覆盖
    def area_not_covered() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}&serviceAreaId={OTHER_AREA_ID}", token=operator_token)
        expect(status == 200, f"HTTP {status}")
        codes = {reason["code"] for reason in payload["data"]["reasons"]}
        expect("SERVICE_AREA_NOT_COVERED" in codes, f"实际 {sorted(codes)}")
        return f"area {OTHER_AREA_ID} 未覆盖"

    check("订单区域不在人员片区内 -> 不可派单", area_not_covered)

    # ---------------------------------------------------------------- 14. 未审核仍不可派单
    def eligibility_before_approve() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}&serviceAreaId={area_id}", token=operator_token)
        expect(status == 200, f"HTTP {status}")
        data = payload["data"]
        codes = {reason["code"] for reason in data["reasons"]}
        expect(data["eligible"] is False, "档案未审核不应可派单")
        expect("WORKER_AUDIT_NOT_APPROVED" in codes, f"实际 {sorted(codes)}")
        expect("SKILL_NOT_VERIFIED" not in codes, "技能已审核，不应再报技能原因")
        expect("CERTIFICATE_EXPIRED" not in codes, "证书已通过，不应再报证书原因")
        return f"剩余原因 {sorted(codes)}"

    check("技能与证书齐备但档案未审核 -> 仍不可派单", eligibility_before_approve)

    # ---------------------------------------------------------------- 15. 审核通过
    def approve_worker() -> str:
        status, payload = api.call("POST", f"/api/v1/operator/workers/{state['worker_no']}/approve",
                                   None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["auditStatus"] == "APPROVED", f"实际 {data['auditStatus']}")
        expect(data["workStatus"] == "ACTIVE", f"审核通过应同时在岗，实际 {data['workStatus']}")
        expect(bool(data["joinedAt"]), "应记录入职时间")
        return f"{data['workStatus']}/{data['auditStatus']}"

    check("审核通过并办理入职", approve_worker)

    # ---------------------------------------------------------------- 16. 核心断言
    def eligible_now() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}&serviceAreaId={area_id}", token=operator_token)
        expect(status == 200, f"HTTP {status}")
        data = payload["data"]
        expect(data["eligible"] is True, f"应可派单，实际原因 {data['reasons']}")
        expect(data["reasons"] == [], "可派单时原因列表必须为空")
        return "eligible=true"

    check("资格齐备 -> 可派单", eligible_now)

    # ---------------------------------------------------------------- 17. 暂停联动角色
    def suspend_worker() -> str:
        status, payload = api.call("POST", f"/api/v1/operator/workers/{state['worker_no']}/suspend",
                                   None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}", token=operator_token)
        codes = {reason["code"] for reason in payload["data"]["reasons"]}
        expect("WORKER_NOT_ACTIVE" in codes, f"实际 {sorted(codes)}")
        expect("WORKER_ROLE_NOT_ACTIVE" in codes, "暂停应同时暂停 WORKER 角色")
        return f"{sorted(codes)}"

    check("暂停接单 -> 人员与角色同时失效", suspend_worker)

    # ---------------------------------------------------------------- 18. 旧令牌失效
    def old_token_invalidated() -> str:
        status, payload = api.call("GET", "/api/v1/worker/profile", token=state["worker_token"])
        expect(status in (401, 403), f"暂停后旧令牌应失效，实际 HTTP {status}")
        return f"{status}/{payload.get('code')}"

    check("暂停后旧令牌立即失效（token_version 自增）", old_token_invalidated)

    # ---------------------------------------------------------------- 19. 恢复
    def reactivate_worker() -> str:
        status, payload = api.call("POST", f"/api/v1/operator/workers/{state['worker_no']}/reactivate",
                                   None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        status, payload = api.call(
            "GET", f"/api/v1/operator/workers/{state['worker_no']}/eligibility"
                   f"?serviceItemNo={item_no}&serviceAreaId={area_id}", token=operator_token)
        expect(payload["data"]["eligible"] is True, f"恢复后应可派单，实际 {payload['data']['reasons']}")
        return "eligible=true"

    check("恢复接单 -> 重新具备资格", reactivate_worker)

    # ---------------------------------------------------------------- 20. 离职终态
    def leave_worker() -> str:
        status, payload = api.call("POST", f"/api/v1/operator/workers/{state['worker_no']}/leave",
                                   None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        status, payload = api.call("GET", f"/api/v1/operator/workers/{state['worker_no']}",
                                   token=operator_token)
        expect(payload["data"]["workStatus"] == "LEFT", f"实际 {payload['data']['workStatus']}")
        return "workStatus=LEFT"

    check("办理离职", leave_worker)

    def cannot_reactivate_left() -> str:
        return expect_error(api, "POST", f"/api/v1/operator/workers/{state['worker_no']}/reactivate",
                            409, "WORKER_STATUS_CONFLICT", None, operator_token)

    check("离职是终态，不可恢复", cannot_reactivate_left)

    # ---------------------------------------------------------------- 21. 越权隔离
    def customer_cannot_access_worker_api() -> str:
        phone = new_phone("136")
        status, payload = api.call("POST", "/api/v1/auth/customer/register", {
            "phone": phone, "password": "Hestia2026", "displayName": "冒烟越权客户"})
        expect(status == 200, f"注册失败 HTTP {status} code={payload.get('code')}")
        customer_token = payload["data"]["accessToken"]
        status, payload = api.call("GET", "/api/v1/worker/profile", token=customer_token)
        expect(status == 403, f"消费者访问服务人员接口应为 403，实际 {status}")
        return f"403/{payload.get('code')}"

    check("消费者访问服务人员接口 -> 403", customer_cannot_access_worker_api)

    # ---------------------------------------------------------------- 汇总
    print()
    print("=" * 78)
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项。")
    if FAILED:
        print("失败项：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("批次 2（人员与资质）冒烟验证全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
