#!/usr/bin/env python3
"""批次 3 订单与派单模块冒烟验证：下单、幂等、支付、改期、取消、超时。

验证的核心命题
--------------
**订单状态推进与周边资源始终同步**：下单冻结时段并挂起派单任务、支付激活
派单、改期换时段不丢派单、取消释放时段并关停派单——任何一步脱节都会留下
"订单说已取消、时段还占着"的脏状态。幂等与越权也在链路里钉死：同键重放
返回同一张单，同键改内容必须冲突，别人的单看一眼都算不存在。

前提
----
1. 应用已启动（local profile），V1~V5 迁移已执行；
2. 库里存在已授 ``OPERATOR`` 角色的账号；
3. 种子服务项目（SVC202610230001）仍在售。

隔离策略
--------
可用性与时段是全库聚合，脚本使用 5~6 天后（远期）日期并独占一名新建人员，
四个订单分别占用 far/AM、far/PM、far2/PM、far2/AM（时序错开），互相不干扰。

安全约定
--------
只打印令牌前 8 位与脱敏手机号。

用法
----
    python scripts/dev/smoke-booking.py
    python scripts/dev/smoke-booking.py --base-url http://127.0.0.1:8080
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta

DEFAULT_ITEM_NO = "SVC202610230001"  # 种子项目，时长 240 分钟

CITY_CODE = "440100"
DISTRICT_CODE = "440106"
STREET_WUSHAN = "440106001"


class Failure(Exception):
    """单条检查失败。"""


class Api:
    """极简 HTTP 客户端：返回 (状态码, 响应体)，不抛 HTTP 错误。"""

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

    def call(self, method: str, path: str, body: dict | None = None,
             token: str | None = None, idem_key: str | None = None) -> tuple[int, dict]:
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        if token:
            headers["Authorization"] = "Bearer " + token
        if idem_key:
            headers["Idempotency-Key"] = idem_key
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
    return prefix + str(int(time.time() * 1000) % 100000000).zfill(8)


def require_detail(api: Api, order_no: str, token: str) -> dict:
    """查询订单详情并要求成功；供"写接口返回空体"的断言链路复用。"""
    status, payload = api.call("GET", f"/api/v1/customer/bookings/{order_no}", token=token)
    expect(status == 200, f"查询订单 {order_no} -> HTTP {status} code={payload.get('code')}")
    return payload["data"]


def address_body(street: str) -> dict:
    return {
        "label": "家",
        "contactName": "冒烟客户",
        "contactPhone": "13900139000",
        "provinceCode": "440000",
        "cityCode": CITY_CODE,
        "districtCode": DISTRICT_CODE,
        "streetCode": street,
        "communityName": "华工教职工小区",
        "buildingNo": "3 栋",
        "unitNo": "2 单元",
        "floorNo": "6",
        "roomNo": "602",
        "detailAddress": "五山路 381 号",
        "isDefault": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="批次 3 订单与派单模块冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--operator-phone", default="13900000001")
    parser.add_argument("--operator-password", default="Hestia2026")
    parser.add_argument("--worker-password", default="Hestia2026")
    parser.add_argument("--customer-password", default="Hestia2026")
    parser.add_argument("--service-item", default=DEFAULT_ITEM_NO)
    args = parser.parse_args()

    api = Api(args.base_url)
    item_no = args.service_item
    worker_phone = new_phone("135")
    phone_a = new_phone("137")
    phone_b = new_phone("138")
    ts = int(time.time())
    # 可用性与时段是全库聚合：每轮用互不相同的远期日期（偏移随时间戳轮转），
    # 避免上一轮冒烟遗留的排班/时段干扰本轮的"可约/不可约"断言
    far = date.today() + timedelta(days=5 + ts % 40)
    far2 = far + timedelta(days=1)
    iso = lambda d: d.isoformat()  # noqa: E731
    state: dict = {}

    print("=" * 78)
    print("批次 3 · 订单与派单模块冒烟验证")
    print(f"接口地址：{args.base_url}")
    print(f"运营账号：{mask_phone(args.operator_phone)}")
    print(f"人员手机号：{mask_phone(worker_phone)}  客户A：{mask_phone(phone_a)}  客户B：{mask_phone(phone_b)}")
    print(f"排班日期：{iso(far)} / {iso(far2)}")
    print("=" * 78)

    # ---------------------------------------------------------------- 前置：运营登录
    status, payload = api.call("POST", "/api/v1/auth/login", {
        "phone": args.operator_phone, "password": args.operator_password})
    if status != 200:
        print(f"[中止] 运营账号登录失败：HTTP {status} code={payload.get('code')}")
        return 2
    operator_token = payload["data"]["accessToken"]
    print(f"运营令牌：{mask(operator_token)}\n")

    # ================================================================ 准备段

    def prepare_public_refs() -> str:
        status, payload = api.call("GET", "/api/v1/public/service-windows")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        for w in payload["data"]:
            state[w["windowCode"]] = w["windowId"]
        expect({"MORNING", "AFTERNOON"} <= set(state), "种子时间窗缺失")
        status, payload = api.call("POST", "/api/v1/public/service-area-match", {
            "cityCode": CITY_CODE, "districtCode": DISTRICT_CODE,
            "streetCode": STREET_WUSHAN})
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data.get("matchStatus") == "MATCHED", f"区域匹配 {data.get('matchStatus')}")
        state["street_area_id"] = data["serviceAreaId"]
        return f"五山片区 id={state['street_area_id']}"

    check("准备：时间窗与五山片区匹配", prepare_public_refs)

    def prepare_worker() -> str:
        status, payload = api.call("POST", "/api/v1/operator/workers", {
            "countryCode": "+86", "phone": worker_phone, "displayName": "订单冒烟人员",
            "realName": "订单测试员", "gender": "MALE", "employmentType": "FULL_TIME",
            "experienceYears": 3, "introduction": "订单冒烟创建", "maxDailyOrders": 3,
        }, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        state["worker_no"] = data["workerNo"]
        for method, path, body in (
                ("PUT", f"/api/v1/operator/service-items/{item_no}/qualification-requirements",
                 {"requirements": []}),
                ("PUT", f"/api/v1/operator/workers/{state['worker_no']}/skills",
                 {"skills": [{"serviceItemNo": item_no, "skillLevel": "INTERMEDIATE",
                              "verified": True}]}),
                ("PUT", f"/api/v1/operator/workers/{state['worker_no']}/service-areas",
                 {"serviceAreaIds": [state["street_area_id"]]}),
        ):
            status, payload = api.call(method, path, body, operator_token)
            expect(status == 200, f"{method} {path} -> HTTP {status} code={payload.get('code')}")
        status, payload = api.call("POST", "/api/v1/auth/worker/activate", {
            "token": data["activationToken"], "password": args.worker_password})
        expect(status == 200, f"activate -> HTTP {status} code={payload.get('code')}")
        state["worker_token"] = payload["data"]["accessToken"]
        status, payload = api.call(
            "POST", f"/api/v1/operator/workers/{state['worker_no']}/approve", None, operator_token)
        expect(status == 200, f"approve -> HTTP {status} code={payload.get('code')}")
        return f"workerNo={state['worker_no']} 审核通过"

    check("准备：人员档案/技能/区域/激活/审核", prepare_worker)

    def prepare_schedules() -> str:
        status, payload = api.call("POST", "/api/v1/operator/schedules/batch", {
            "workerNo": state["worker_no"],
            "serviceDates": [iso(far), iso(far2)],
            "windowIds": [state["MORNING"], state["AFTERNOON"]],
            "scheduleType": "WORK", "note": "订单冒烟排班",
        }, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        schedules = payload["data"]
        expect(len(schedules) == 4, f"应生成 4 条排班，实际 {len(schedules)}")
        for s in schedules:
            expect(s["slotStatus"] == "AVAILABLE", f"WORK 排班应生成 AVAILABLE 时段 {s}")
        return "far/far2 × 早/午 4 条排班"

    check("准备：运营批量排班（远期 4 窗口）", prepare_schedules)

    def prepare_customers() -> str:
        for phone, key, name in ((phone_a, "tokenA", "冒烟客户A"), (phone_b, "tokenB", "冒烟客户B")):
            status, payload = api.call("POST", "/api/v1/auth/customer/register", {
                "phone": phone, "password": args.customer_password, "displayName": name})
            expect(status == 200, f"注册 {key} -> HTTP {status} code={payload.get('code')}")
            state[key] = payload["data"]["accessToken"]
        status, payload = api.call("POST", "/api/v1/customer/addresses",
                                   address_body(STREET_WUSHAN), token=state["tokenA"])
        expect(status == 200, f"创建地址 -> HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data.get("areaMatchStatus") == "MATCHED", f"地址匹配 {data.get('areaMatchStatus')}")
        state["address_no"] = data["addressNo"]
        return "客户 A/B 注册 + 地址匹配五山片区"

    check("准备：两个客户与默认地址", prepare_customers)

    # ================================================================ 1. 预览价

    def preview_price() -> str:
        # 种子项目 max_quantity=1.00，数量越界是正确的目录校验行为；
        # 这里验证定价确定性：同一请求两次预览结果必须一致（服务端重算）
        body = {"serviceItemNo": item_no, "appointmentDate": iso(far),
                "serviceWindowCode": "MORNING", "addressNo": state["address_no"],
                "quantity": 1}
        status, payload = api.call("POST", "/api/v1/customer/bookings/preview", body,
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        p1 = payload["data"]["payableAmount"]
        expect(p1 > 0, f"预览价应为正，实际 {p1}")
        status, payload = api.call("POST", "/api/v1/customer/bookings/preview", body,
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        p2 = payload["data"]["payableAmount"]
        expect(abs(p2 - p1) < 0.001, f"定价应确定：两次预览 {p1} != {p2}")
        state["price"] = p1
        return f"定价确定，单价 {p1}"

    check("预览价确定且为正（服务端重算）", preview_price)

    # ================================================================ 2. 下单 O1 + 幂等 + 越权 + 占用

    def create_order_o1() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(far),
            "serviceWindowCode": "MORNING", "addressNo": state["address_no"],
            "quantity": 1}, token=state["tokenA"], idem_key=f"smoke-b11-o1-{ts}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["orderStatus"] == "PENDING_PAYMENT", f"orderStatus={data['orderStatus']}")
        expect(abs(data["payableAmount"] - state["price"]) < 0.001,
               f"下单价 {data['payableAmount']} 应等于预览价 {state['price']}")
        expect(bool(data.get("paymentDeadline")), "应返回支付截止时间")
        state["o1"] = data["orderNo"]
        return f"orderNo={data['orderNo']} 金额 {data['payableAmount']}"

    check("下单 O1 -> PENDING_PAYMENT 且价与预览一致", create_order_o1)

    def idempotent_replay() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(far),
            "serviceWindowCode": "MORNING", "addressNo": state["address_no"],
            "quantity": 1}, token=state["tokenA"], idem_key=f"smoke-b11-o1-{ts}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["orderNo"] == state["o1"],
               f"重放应返回同一单号，实际 {payload['data']['orderNo']}")
        return f"同键重放 -> 同单 {state['o1']}"

    check("同幂等键同内容重放 -> 返回同一订单", idempotent_replay)

    # 同键不同内容：手动带键调用（expect_error 不支持 Idempotency-Key 头）
    def idempotent_conflict_call() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(far),
            "serviceWindowCode": "MORNING", "addressNo": state["address_no"],
            "quantity": 2}, token=state["tokenA"], idem_key=f"smoke-b11-o1-{ts}")
        expect(status == 409, f"期望 HTTP 409，实际 {status}（code={payload.get('code')}）")
        expect(payload.get("code") == "IDEMPOTENCY_CONFLICT",
               f"期望 IDEMPOTENCY_CONFLICT，实际 {payload.get('code')}")
        return "409/IDEMPOTENCY_CONFLICT"

    check("同幂等键不同内容 -> 冲突", idempotent_conflict_call)

    def cross_customer_read() -> str:
        return expect_error(api, "GET", f"/api/v1/customer/bookings/{state['o1']}",
                            404, "BOOKING_NOT_OWNED", token=state["tokenB"])

    check("客户 B 读客户 A 订单 -> 404 越权隔离", cross_customer_read)

    def slot_held_after_create() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/public/availability?serviceItemNo={item_no}"
                   f"&serviceAreaId={state['street_area_id']}&dateFrom={iso(far)}&dateTo={iso(far)}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        windows = {w["windowCode"]: w["available"] for w in payload["data"][0]["windows"]}
        expect(windows.get("MORNING") is False,
               f"下单后唯一可用时段被占，MORNING 应不可约：{windows}")
        expect(windows.get("AFTERNOON") is True,
               f"未占用窗口仍应可约：{windows}")
        return "MORNING 不可约 / AFTERNOON 仍可约"

    check("下单冻结时段 -> 可用性聚合变化", slot_held_after_create)

    # ================================================================ 3. 取消待支付 O1 -> 释放

    def cancel_pending_o1() -> str:
        status, payload = api.call("POST", f"/api/v1/customer/bookings/{state['o1']}/cancel",
                                   {"reasonCode": "CUSTOMER_CANCEL", "reasonText": "冒烟取消"},
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        # 取消接口返回空体，断言走重查详情
        detail = require_detail(api, state["o1"], state["tokenA"])
        expect(detail["orderStatus"] == "CANCELED", f"orderStatus={detail['orderStatus']}")
        expect(detail.get("canceledAt"), "应记录取消时间")
        expect(detail.get("assignmentStatus") == "CANCELED",
               f"取消后派单摘要应同步 CANCELED，实际 {detail.get('assignmentStatus')}")
        return f"{state['o1']} -> CANCELED"

    check("取消待支付订单 -> CANCELED", cancel_pending_o1)

    def cancel_pending_twice() -> str:
        return expect_error(api, "POST", f"/api/v1/customer/bookings/{state['o1']}/cancel",
                            409, "BOOKING_CANNOT_CANCEL", None, token=state["tokenA"])

    check("重复取消 -> 409 状态机拒绝", cancel_pending_twice)

    def slot_released_after_cancel() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/public/availability?serviceItemNo={item_no}"
                   f"&serviceAreaId={state['street_area_id']}&dateFrom={iso(far)}&dateTo={iso(far)}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        windows = {w["windowCode"]: w["available"] for w in payload["data"][0]["windows"]}
        expect(windows.get("MORNING") is True,
               f"取消后时段应释放回可约：{windows}")
        return "MORNING 恢复可约"

    check("取消释放时段 -> 可用性恢复", slot_released_after_cancel)

    # ================================================================ 4. 支付 O2（far AFTERNOON）

    def create_order_o2() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(far),
            "serviceWindowCode": "AFTERNOON", "addressNo": state["address_no"],
            "quantity": 1}, token=state["tokenA"], idem_key=f"smoke-b11-o2-{ts}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["o2"] = payload["data"]["orderNo"]
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o2']}",
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        detail = payload["data"]
        expect(detail["assignmentStatus"] == "WAITING_PAYMENT",
               f"待支付订单派单应挂起，实际 assignmentStatus={detail['assignmentStatus']}")
        return f"orderNo={state['o2']} assignment=WAITING_PAYMENT"

    check("下单 O2 -> 派单任务 WAITING_PAYMENT", create_order_o2)

    def pay_wrong_amount() -> str:
        return expect_error(api, "POST",
                            f"/api/v1/system/smoke/booking/{state['o2']}/mark-paid",
                            422, "PAYMENT_AMOUNT_MISMATCH",
                            {"paidAmount": state["price"] + 0.01})

    check("支付金额不符 -> 422 拒绝", pay_wrong_amount)

    def pay_correct() -> str:
        status, payload = api.call("POST",
                                   f"/api/v1/system/smoke/booking/{state['o2']}/mark-paid",
                                   {"paidAmount": state["price"]})
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        detail = payload["data"]
        expect(detail["orderStatus"] == "CONFIRMED", f"orderStatus={detail['orderStatus']}")
        expect(detail["paymentStatus"] == "PAID", f"paymentStatus={detail['paymentStatus']}")
        expect(detail["assignmentStatus"] == "PENDING_ACCEPT",
               f"支付后派单应待接单，实际 assignmentStatus={detail['assignmentStatus']}")
        expect(detail.get("paidAt"), "应记录支付时间")
        expect(detail.get("currentWorkerId"), "支付激活派单后应已锁定服务人员")
        return f"{state['o2']} -> CONFIRMED + PENDING_ACCEPT"

    check("模拟支付成功 -> CONFIRMED 且派单 PENDING_ACCEPT", pay_correct)

    def pay_twice_idempotent() -> str:
        status, payload = api.call("POST",
                                   f"/api/v1/system/smoke/booking/{state['o2']}/mark-paid",
                                   {"paidAmount": state["price"]})
        expect(status == 200, f"重复支付应幂等返回，实际 HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["orderStatus"] == "CONFIRMED", "重复支付后仍应 CONFIRMED")
        return "重复回调幂等返回 CONFIRMED"

    check("重复支付回调 -> 幂等无副作用", pay_twice_idempotent)

    # ================================================================ 5. 改期 O2 -> far2 MORNING

    def reschedule_o2() -> str:
        status, payload = api.call("POST", f"/api/v1/customer/bookings/{state['o2']}/reschedule",
                                   {"appointmentDate": iso(far2), "serviceWindowCode": "MORNING"},
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        # 改期接口返回空体，断言走重查详情
        detail = require_detail(api, state["o2"], state["tokenA"])
        expect(detail["appointmentDate"] == iso(far2), f"appointmentDate={detail['appointmentDate']}")
        expect(detail["orderStatus"] == "CONFIRMED", f"orderStatus={detail['orderStatus']}")
        expect(detail["assignmentStatus"] == "PENDING_ACCEPT",
               f"改期后派单应保持待接单，实际 {detail['assignmentStatus']}")
        return f"{state['o2']} -> {iso(far2)} MORNING"

    check("改期已支付订单 -> 日期更新且派单保持", reschedule_o2)

    def worker_sees_assignment() -> str:
        status, payload = api.call("GET", "/api/v1/worker/bookings?page=1&size=20",
                                   token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        records = payload["data"]["records"] if isinstance(payload["data"], dict) else payload["data"]
        mine = [r for r in records if r["orderNo"] == state["o2"]]
        expect(mine, f"人员任务列表应包含 {state['o2']}，实际 {[r['orderNo'] for r in records]}")
        expect(mine[0]["appointmentDate"] == iso(far2),
               f"人员看到的日期应为改期后 {iso(far2)}")
        return f"任务列表含 {state['o2']}（{iso(far2)}）"

    check("人员视角可见被派订单", worker_sees_assignment)

    # ================================================================ 6. 已支付取消 O3（far2 AFTERNOON）

    def create_pay_cancel_o3() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(far2),
            "serviceWindowCode": "AFTERNOON", "addressNo": state["address_no"],
            "quantity": 1}, token=state["tokenA"], idem_key=f"smoke-b11-o3-{ts}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["o3"] = payload["data"]["orderNo"]
        status, payload = api.call("POST",
                                   f"/api/v1/system/smoke/booking/{state['o3']}/mark-paid",
                                   {"paidAmount": state["price"]})
        expect(status == 200, f"支付 -> HTTP {status} code={payload.get('code')}")
        # 远期（>= 5 天后）取消，超过免费窗口 24h，应零费用
        status, payload = api.call("POST", f"/api/v1/customer/bookings/{state['o3']}/cancel",
                                   {"reasonCode": "CUSTOMER_CANCEL", "reasonText": "冒烟已付取消"},
                                   token=state["tokenA"])
        expect(status == 200, f"取消 -> HTTP {status} code={payload.get('code')}")
        detail = require_detail(api, state["o3"], state["tokenA"])
        expect(detail["orderStatus"] == "CANCELED", f"orderStatus={detail['orderStatus']}")
        expect(detail.get("assignmentStatus") == "CANCELED",
               f"已支付取消后派单摘要应同步 CANCELED，实际 {detail.get('assignmentStatus')}")
        return f"{state['o3']} 已支付后取消 -> CANCELED"

    check("已支付订单远期取消 -> CANCELED", create_pay_cancel_o3)

    def cancel_paid_twice() -> str:
        return expect_error(api, "POST", f"/api/v1/customer/bookings/{state['o3']}/cancel",
                            409, "BOOKING_CANNOT_CANCEL", None, token=state["tokenA"])

    check("已取消订单再取消 -> 409", cancel_paid_twice)

    # ================================================================ 7. 支付超时 O4（复用 far2 AFTERNOON）

    def create_order_o4() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(far2),
            "serviceWindowCode": "AFTERNOON", "addressNo": state["address_no"],
            "quantity": 1}, token=state["tokenA"], idem_key=f"smoke-b11-o4-{ts}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["o4"] = payload["data"]["orderNo"]
        return f"orderNo={state['o4']} 不支付等待超时"

    check("下单 O4 后放置不支付", create_order_o4)

    def timeout_sweep() -> str:
        # 注入模拟时钟：视作明天中午，刚下的 O4（15 分钟支付窗口）即已超时。
        # 本轮结束时其他订单均已终态（取消/已支付），不会被误扫。
        as_of = (date.today() + timedelta(days=1)).isoformat() + "T12:00:00"
        status, payload = api.call(
            "POST", f"/api/v1/system/smoke/booking/timeout-sweep?asOf={as_of}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        handled = payload["data"]["handled"]
        expect(handled >= 1, f"超时扫描应至少处理 1 单，实际 handled={handled}")
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o4']}",
                                   token=state["tokenA"])
        detail = payload["data"]
        expect(detail["orderStatus"] == "CANCELED", f"超时后应 CANCELED，实际 {detail['orderStatus']}")
        expect(detail.get("cancelReasonCode") == "PAYMENT_TIMEOUT",
               f"取消原因应为 PAYMENT_TIMEOUT，实际 {detail.get('cancelReasonCode')}")
        return f"sweep 处理 {handled} 单，{state['o4']} -> CANCELED(PAYMENT_TIMEOUT)"

    check("支付超时扫描自动关单", timeout_sweep)

    # ================================================================ 8. 收尾视图一致性

    def operator_sees_all() -> str:
        status, payload = api.call("GET", "/api/v1/operator/bookings?page=1&size=50",
                                   token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        records = payload["data"]["records"] if isinstance(payload["data"], dict) else payload["data"]
        nos = {r["orderNo"] for r in records}
        for order_no in (state["o1"], state["o2"], state["o3"], state["o4"]):
            expect(order_no in nos, f"运营列表缺订单 {order_no}")
        return f"运营列表含全部 {len([state['o1'], state['o2'], state['o3'], state['o4']])} 单"

    check("运营视角订单列表完整", operator_sees_all)

    # ---------------------------------------------------------------- 汇总
    print("=" * 78)
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项")
    if FAILED:
        print("失败项：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("订单与派单链路全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
