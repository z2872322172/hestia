#!/usr/bin/env python3
"""批次 3 第二轮冒烟验证：真实支付 → 派单接单 → 履约 → 评价 → 售后退款 → 质量汇总。

验证的核心命题
--------------
**一条真实业务链路全绿**：支付必须走「创建支付单 + 渠道回调验签」而不是测试后门；
人员接单后履约记录自动建立；履约推进与订单主状态始终同步；只有完成订单可以
评价与发起售后；售后工单只形成结论——退款委托支付模块执行，工单不碰支付表。

前提
----
1. 应用已启动（local profile），V1~V6 迁移已执行；
2. 库里存在已授 ``OPERATOR`` 角色的账号；
3. 种子服务项目（SVC202610230001，时长 240 分钟）仍在售。

窗口自适应
----------
签到窗口为 ``[预约开始-60min, 预约结束+120min]``：脚本按当前时间挑今天可用的
MORNING / AFTERNOON 窗口跑完整履约链路；深夜（窗口都已关闭）自动切换夜间模式，
断言签到被 409 拒绝后由运营强制完成，链路其余环节保持覆盖。

用法
----
    python scripts/dev/smoke-batch3.py
    python scripts/dev/smoke-batch3.py --base-url http://127.0.0.1:18080
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from decimal import Decimal

DEFAULT_ITEM_NO = "SVC202610230001"

CITY_CODE = "440100"
DISTRICT_CODE = "440106"
STREET_WUSHAN = "440106001"

MOCK_GATEWAY_SECRET = "hestia-mock-gateway-secret"

REVIEW_DIMENSIONS = {"SERVICE": 4.5, "ATTITUDE": 5, "QUALITY": 4, "PUNCTUALITY": 4.5}


class Failure(Exception):
    """单条检查失败。"""


class Api:
    """极简 HTTP 客户端：返回 (状态码, 响应体)，不抛 HTTP 错误。

    ``parse_float=Decimal``：金额必须保留两位小数字符串口径，
    否则 Mock 回调签名会因 ``12.0 != 12.00`` 校验失败。
    """

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

    def call(self, method: str, path: str, body: dict | None = None,
             token: str | None = None, idem_key: str | None = None) -> tuple[int, dict]:
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False, default=str).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        if token:
            headers["Authorization"] = "Bearer " + token
        if idem_key:
            headers["Idempotency-Key"] = idem_key
        request = urllib.request.Request(self.base_url + path, data=data,
                                         headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                raw = response.read().decode("utf-8")
                return response.status, json.loads(raw, parse_float=Decimal)
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


def pick_window(now: datetime) -> tuple[date, str, bool]:
    """按当前时间挑可用窗口：返回 (预约日期, 窗口编码, 是否夜间模式)。

    签到窗口 = [start-60min, end+120min]：
    MORNING 覆盖 07:00-14:00，AFTERNOON 覆盖 11:00-19:00。
    19:00 之后与 07:00 之前无窗口可用，进入夜间模式（远期单 + 强制完成）。
    """
    today = date.today()
    hour = now.hour
    if 7 <= hour < 11:
        return today, "MORNING", False
    if 11 <= hour < 17:
        return today, "AFTERNOON", False
    return today + timedelta(days=6), "MORNING", True


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
    parser = argparse.ArgumentParser(description="批次 3 第二轮全链路冒烟验证")
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
    ts = int(time.time())
    book_date, window_code, night_mode = pick_window(datetime.now())
    iso = lambda d: d.isoformat()  # noqa: E731
    state: dict = {}

    print("=" * 78)
    print("批次 3 · 支付/派单/履约/售后 全链路冒烟验证")
    print(f"接口地址：{args.base_url}")
    print(f"运营账号：{mask_phone(args.operator_phone)}  人员：{mask_phone(worker_phone)}"
          f"  客户A：{mask_phone(phone_a)}")
    print(f"预约：{iso(book_date)} {window_code}"
          + ("（夜间模式：签到出窗断言 + 运营强制完成）" if night_mode else ""))
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
            "countryCode": "+86", "phone": worker_phone, "displayName": "全链路冒烟人员",
            "realName": "链路测试员", "gender": "MALE", "employmentType": "FULL_TIME",
            "experienceYears": 3, "introduction": "批次3冒烟创建", "maxDailyOrders": 3,
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
            "serviceDates": [iso(book_date)],
            "windowIds": [state["MORNING"], state["AFTERNOON"]],
            "scheduleType": "WORK", "note": "批次3冒烟排班",
        }, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        return f"{iso(book_date)} × 早/午排班"

    check("准备：运营批量排班", prepare_schedules)

    def prepare_customer() -> str:
        status, payload = api.call("POST", "/api/v1/auth/customer/register", {
            "phone": phone_a, "password": args.customer_password, "displayName": "全链路冒烟客户"})
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["tokenA"] = payload["data"]["accessToken"]
        status, payload = api.call("POST", "/api/v1/customer/addresses",
                                   address_body(STREET_WUSHAN), token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data.get("areaMatchStatus") == "MATCHED", f"地址匹配 {data.get('areaMatchStatus')}")
        state["address_no"] = data["addressNo"]
        return f"客户A注册 + 地址命中片区 {data.get('serviceAreaId')}"

    check("准备：客户与默认地址", prepare_customer)

    # ================================================================ 1. 下单与前置校验

    def create_order_o1() -> str:
        status, payload = api.call("POST", "/api/v1/customer/bookings", {
            "serviceItemNo": item_no, "appointmentDate": iso(book_date),
            "serviceWindowCode": window_code, "addressNo": state["address_no"],
            "quantity": 1}, token=state["tokenA"], idem_key=f"smoke-b3-o1-{ts}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["orderStatus"] == "PENDING_PAYMENT", f"orderStatus={data['orderStatus']}")
        state["o1"] = data["orderNo"]
        state["payable"] = data["payableAmount"]
        return f"orderNo={data['orderNo']} 金额 {data['payableAmount']}"

    check("下单 O1 -> PENDING_PAYMENT", create_order_o1)

    def review_blocked_before_complete() -> str:
        return expect_error(api, "POST", f"/api/v1/customer/bookings/{state['o1']}/reviews",
                            409, "REVIEW_NOT_ALLOWED",
                            {"dimensionScores": {"SERVICE": 5, "ATTITUDE": 5,
                                                 "QUALITY": 5, "PUNCTUALITY": 5}},
                            token=state["tokenA"])

    check("未完成订单评价 -> 409 REVIEW_NOT_ALLOWED", review_blocked_before_complete)

    def aftersale_blocked_before_confirmed() -> str:
        return expect_error(api, "POST", f"/api/v1/customer/bookings/{state['o1']}/after-sales",
                            409, "AFTER_SALE_NOT_ALLOWED",
                            {"ticketType": "LATE_ARRIVAL", "description": "冒烟售后"},
                            token=state["tokenA"])

    check("未确认订单售后 -> 409 AFTER_SALE_NOT_ALLOWED", aftersale_blocked_before_confirmed)

    # ================================================================ 2. 真实支付（pay + 回调验签）

    def create_payment() -> str:
        status, payload = api.call("POST", f"/api/v1/customer/bookings/{state['o1']}/pay",
                                   {"channel": "MOCK"}, token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(bool(data.get("paymentNo")), "未返回支付单号")
        expect(data.get("amount") == state["payable"],
               f"支付单金额 {data.get('amount')} 与订单应付 {state['payable']} 不一致")
        state["payment_no"] = data["paymentNo"]
        state["payment_amount"] = data["amount"]
        return f"paymentNo={data['paymentNo']} status={data.get('status')}"

    check("创建支付单（MOCK 渠道）", create_payment)

    def callback_bad_sign() -> str:
        sign = hashlib.sha256(b"tampered").hexdigest()
        return expect_error(api, "POST", "/api/v1/payment/callback/mock", 400,
                            "PAYMENT_CALLBACK_INVALID", {
                                "paymentNo": state["payment_no"],
                                "callbackId": f"cb-bad-{ts}",
                                "amount": str(state["payment_amount"]),
                                "sign": sign})

    check("签名错误的回调 -> 400 PAYMENT_CALLBACK_INVALID", callback_bad_sign)

    def callback_success() -> str:
        material = f"{state['payment_no']}|{state['payment_amount']}|{MOCK_GATEWAY_SECRET}"
        sign = hashlib.sha256(material.encode("utf-8")).hexdigest()
        status, payload = api.call("POST", "/api/v1/payment/callback/mock", {
            "paymentNo": state["payment_no"], "callbackId": f"cb-ok-{ts}",
            "tradeNo": f"TR{ts}", "amount": str(state["payment_amount"]),
            "sign": sign})
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        expect(payload["data"]["status"] == "SUCCESS", f"支付状态 {payload['data']['status']}")
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o1']}",
                                   token=state["tokenA"])
        data = payload["data"]
        expect(data["orderStatus"] == "CONFIRMED", f"orderStatus={data['orderStatus']}")
        expect(data["paymentStatus"] == "PAID", f"paymentStatus={data['paymentStatus']}")
        return "订单 CONFIRMED + 支付摘要 PAID"

    check("正确签名回调 -> 支付成功、订单确认", callback_success)

    def callback_duplicate() -> str:
        material = f"{state['payment_no']}|{state['payment_amount']}|{MOCK_GATEWAY_SECRET}"
        sign = hashlib.sha256(material.encode("utf-8")).hexdigest()
        status, payload = api.call("POST", "/api/v1/payment/callback/mock", {
            "paymentNo": state["payment_no"], "callbackId": f"cb-ok-{ts}",
            "tradeNo": f"TR{ts}", "amount": str(state["payment_amount"]),
            "sign": sign})
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["status"] == "DUPLICATE_CALLBACK",
               f"重复回调应返回 DUPLICATE_CALLBACK，实际 {payload['data']['status']}")
        return "200/DUPLICATE_CALLBACK"

    check("重复回调幂等 -> DUPLICATE_CALLBACK", callback_duplicate)

    def pay_twice_rejected() -> str:
        return expect_error(api, "POST", f"/api/v1/customer/bookings/{state['o1']}/pay",
                            409, "PAYMENT_ORDER_NOT_PAYABLE", {"channel": "MOCK"},
                            token=state["tokenA"])

    check("已支付订单重复支付 -> 409 PAYMENT_ORDER_NOT_PAYABLE", pay_twice_rejected)

    # ================================================================ 3. 派单接单

    def reassign_to_test_worker() -> str:
        """运营手工改派到脚本人员（自动评分同分时选中人不固定，显式改派钉死）。"""
        status, payload = api.call(
            "GET", f"/api/v1/customer/bookings/{state['o1']}/payments",
            token=state["tokenA"])
        expect(status == 200, f"支付流水 HTTP {status} code={payload.get('code')}")
        payments = payload["data"] or []
        mine = [p for p in payments if p.get("paymentNo") == state["payment_no"]]
        expect(len(mine) == 1, "支付流水中未找到本次支付单")
        booking_id = mine[0]["bookingId"]
        state["booking_id"] = booking_id
        status, payload = api.call(
            "GET", "/api/v1/operator/dispatch/jobs?status=PENDING_ACCEPT&page=1&size=50",
            token=operator_token)
        expect(status == 200, f"任务队列 HTTP {status} code={payload.get('code')}")
        jobs = (payload.get("data") or {}).get("records") or []
        job = next((j for j in jobs if j.get("bookingId") == booking_id), None)
        expect(job is not None, f"任务队列中未找到订单 bookingId={booking_id} 的任务")
        state["job_no"] = job["jobNo"]
        status, payload = api.call(
            "GET", f"/api/v1/operator/dispatch/jobs/{state['job_no']}/candidates",
            token=operator_token)
        expect(status == 200, f"候选 HTTP {status} code={payload.get('code')}")
        candidates = payload["data"] or []
        expect(len(candidates) >= 2, f"候选人数 {len(candidates)} 不足（无改派余地）")
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o1']}",
                                   token=state["tokenA"])
        current_worker = payload["data"]["currentWorkerId"]
        target = next((c for c in candidates if c["workerId"] != current_worker), None)
        expect(target is not None, "候选中找不到脚本人员")
        state["worker_id"] = target["workerId"]
        status, payload = api.call(
            "POST", f"/api/v1/operator/dispatch/jobs/{state['job_no']}/reassign",
            {"workerId": state["worker_id"], "reasonText": "冒烟改派：指定链路测试人员"},
            operator_token)
        expect(status == 200, f"改派 HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        view = payload["data"]
        latest = max(view["assignments"], key=lambda a: a["id"])
        expect(latest["workerId"] == state["worker_id"],
               f"改派后最新分配人员 {latest['workerId']}")
        expect(latest["status"] == "PENDING_ACCEPT",
               f"改派后分配状态 {latest['status']}")
        expect(bool(latest.get("dispatchReason")), "改派原因未落审计")
        return f"jobNo={state['job_no']} 改派 -> workerId={state['worker_id']}"

    check("运营手工改派 -> 指定脚本人员（原因落审计）", reassign_to_test_worker)

    def worker_sees_assignment() -> str:
        status, payload = api.call("GET", "/api/v1/worker/assignments?page=1&size=20",
                                   token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        records = (payload.get("data") or {}).get("records") or []
        mine = [r for r in records if r.get("bookingId") == state["booking_id"]]
        expect(len(mine) == 1, f"待接列表应含订单 bookingId={state['booking_id']}，实际 {len(mine)} 条")
        expect(mine[0]["status"] == "PENDING_ACCEPT",
               f"assignment status={mine[0]['status']}")
        state["assignment_no"] = mine[0]["assignmentNo"]
        return f"assignmentNo={state['assignment_no']}"

    check("人员在待接列表看到指派", worker_sees_assignment)

    def worker_accepts() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/worker/assignments/{state['assignment_no']}/accept",
            None, token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        expect(payload["data"]["status"] == "ACCEPTED",
               f"assignment status={payload['data']['status']}")
        return f"{state['assignment_no']} -> ACCEPTED"

    check("人员接单 -> ACCEPTED + 履约记录创建", worker_accepts)

    def accept_twice_rejected() -> str:
        return expect_error(api,
                            "POST", f"/api/v1/worker/assignments/{state['assignment_no']}/accept",
                            409, "DISPATCH_JOB_CONFLICT", None, token=state["worker_token"])

    check("重复接单 -> 409 DISPATCH_JOB_CONFLICT", accept_twice_rejected)

    def fulfillment_created() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/worker/bookings/{state['o1']}/fulfillment",
            token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data.get("status") == "PENDING", f"履约初始状态应为 PENDING，实际 {data.get('status')}")
        expect(bool(data.get("recordNo")), "未返回履约单号")
        state["record_no"] = data["recordNo"]
        state["checklist"] = data.get("checklist") or []
        return f"recordNo={data['recordNo']} 检查项 {len(state['checklist'])} 条"

    check("接单后履约记录 PENDING（含服务标准检查项）", fulfillment_created)

    # ================================================================ 4. 履约推进

    def worker_departs() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/worker/bookings/{state['o1']}/depart",
            None, token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        expect(payload["data"]["status"] == "EN_ROUTE", f"status={payload['data']['status']}")
        return "履约 EN_ROUTE + 订单 EN_ROUTE"

    check("人员出发 -> 履约与订单同步 EN_ROUTE", worker_departs)

    def complete_required_items() -> str:
        done = 0
        for item in state["checklist"]:
            if not item.get("required") or item.get("completed"):
                continue
            if item.get("evidenceRequired"):
                status, payload = api.call(
                    "POST", f"/api/v1/worker/bookings/{state['o1']}/evidence",
                    {"evidenceType": "CHECKLIST_PHOTO",
                     "fileId": 900000 + int(time.time() * 1000) % 90000000,
                     "checklistItemId": item["id"], "description": "冒烟证据"},
                    token=state["worker_token"])
                expect(status == 200,
                       f"上传证据 -> HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            status, payload = api.call(
                "PUT", f"/api/v1/worker/bookings/{state['o1']}/checklist/{item['id']}",
                {"remark": "冒烟完成"}, token=state["worker_token"])
            expect(status == 200, f"完成检查项 {item['id']} -> HTTP {status} code={payload.get('code')}")
            done += 1
        return f"完成必填检查项 {done} 条"

    if night_mode:
        def check_in_out_of_window() -> str:
            return expect_error(api, "POST", f"/api/v1/worker/bookings/{state['o1']}/check-in",
                                409, "FULFILLMENT_CHECK_IN_OUT_OF_WINDOW",
                                {"longitude": 113.36, "latitude": 23.16, "source": "GPS"},
                                token=state["worker_token"])

        check("夜间模式：签到超出预约窗口 -> 409", check_in_out_of_window)

        def worker_reports_exception() -> str:
            status, payload = api.call(
                "POST", f"/api/v1/worker/bookings/{state['o1']}/exceptions",
                {"exceptionType": "OTHER",
                 "description": "夜间冒烟：预约窗口无法到场签到，上报现场异常"},
                token=state["worker_token"])
            expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            expect(payload["data"]["status"] == "EXCEPTION",
                   f"status={payload['data']['status']}")
            return "履约 EXCEPTION（EN_ROUTE 上报异常）"

        check("夜间模式：上报现场异常 -> EXCEPTION", worker_reports_exception)

        def operator_force_complete() -> str:
            status, payload = api.call(
                "POST", f"/api/v1/operator/fulfillment/{state['o1']}/force-complete",
                None, operator_token)
            expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            expect(payload["data"]["status"] == "COMPLETED",
                   f"status={payload['data']['status']}")
            return "履约 COMPLETED（EXCEPTION 强制完成）"

        check("夜间模式：运营强制完成", operator_force_complete)
    else:
        def worker_checks_in() -> str:
            status, payload = api.call(
                "POST", f"/api/v1/worker/bookings/{state['o1']}/check-in",
                {"longitude": 113.36, "latitude": 23.16, "source": "GPS"},
                token=state["worker_token"])
            expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            expect(payload["data"]["status"] == "CHECKED_IN",
                   f"status={payload['data']['status']}")
            return "履约 CHECKED_IN（窗口内）"

        check("人员签到 -> CHECKED_IN", worker_checks_in)

        def worker_starts() -> str:
            status, payload = api.call(
                "POST", f"/api/v1/worker/bookings/{state['o1']}/start",
                None, token=state["worker_token"])
            expect(status == 200, f"HTTP {status} code={payload.get('code')}")
            expect(payload["data"]["status"] == "IN_SERVICE",
                   f"status={payload['data']['status']}")
            return "履约 IN_SERVICE + 订单 IN_SERVICE"

        check("开始服务 -> IN_SERVICE", worker_starts)

        check("完成全部必填检查项（证据先行）", complete_required_items)

        def worker_requests_complete() -> str:
            status, payload = api.call(
                "POST", f"/api/v1/worker/bookings/{state['o1']}/complete-request",
                {"serviceResult": "服务完成，冒烟验证"}, token=state["worker_token"])
            expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            expect(payload["data"]["status"] == "PENDING_CONFIRM",
                   f"status={payload['data']['status']}")
            return "履约 PENDING_CONFIRM"

        check("申请完成 -> PENDING_CONFIRM（必填校验通过）", worker_requests_complete)

        def customer_confirms() -> str:
            status, payload = api.call(
                "POST", f"/api/v1/customer/bookings/{state['o1']}/confirm",
                None, token=state["tokenA"])
            expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            expect(payload["data"]["status"] == "COMPLETED",
                   f"status={payload['data']['status']}")
            return "履约 COMPLETED + 订单 COMPLETED"

        check("客户确认 -> 订单 COMPLETED", customer_confirms)

    # ================================================================ 5. 评价

    def submit_review() -> str:
        status, payload = api.call("POST", f"/api/v1/customer/bookings/{state['o1']}/reviews",
                                   {"dimensionScores": REVIEW_DIMENSIONS,
                                    "content": "冒烟评价：服务不错"},
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(len(data.get("dimensions") or []) == len(REVIEW_DIMENSIONS),
               f"维度分数应 {len(REVIEW_DIMENSIONS)} 条，实际 {len(data.get('dimensions') or [])}")
        overall = Decimal(str(data["overallScore"]))
        expect(overall == Decimal("4.50"), f"综合分应为 4.50，实际 {overall}")
        state["review_no"] = data["reviewNo"]
        return f"reviewNo={data['reviewNo']} 综合分 {data['overallScore']}"

    check("客户评价 -> 综合分 4.50 + REVIEWED", submit_review)

    def review_twice_rejected() -> str:
        return expect_error(api, "POST", f"/api/v1/customer/bookings/{state['o1']}/reviews",
                            409, "REVIEW_ALREADY_EXISTS",
                            {"dimensionScores": REVIEW_DIMENSIONS}, token=state["tokenA"])

    check("重复评价 -> 409 REVIEW_ALREADY_EXISTS", review_twice_rejected)

    def worker_quality_summary() -> str:
        status, payload = api.call("GET", "/api/v1/worker/reviews/summary",
                                   token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data.get("reviewCount") == 1, f"reviewCount={data.get('reviewCount')}")
        expect(Decimal(str(data.get("averageRating"))) == Decimal("4.50"),
               f"averageRating={data.get('averageRating')}")
        expect(data.get("qualityScore") == 90, f"qualityScore={data.get('qualityScore')}")
        return (f"reviewCount={data.get('reviewCount')} avg={data.get('averageRating')} "
                f"qualityScore={data.get('qualityScore')}")

    check("人员质量摘要 -> 平均 4.50 / 派单分 90", worker_quality_summary)

    # ================================================================ 6. 售后工单与退款联动

    def create_ticket() -> str:
        status, payload = api.call("POST", f"/api/v1/customer/bookings/{state['o1']}/after-sales",
                                   {"ticketType": "LATE_ARRIVAL", "priority": "HIGH",
                                    "description": "人员迟到 40 分钟，冒烟工单"},
                                   token=state["tokenA"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["status"] == "SUBMITTED", f"status={data['status']}")
        expect(data.get("dueAt"), "应按优先级计算处理时限 dueAt")
        state["ticket_no"] = data["ticketNo"]
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o1']}",
                                   token=state["tokenA"])
        expect(payload["data"]["afterSaleStatus"] == "PROCESSING",
               f"订单售后摘要应为 PROCESSING，实际 {payload['data']['afterSaleStatus']}")
        return f"ticketNo={data['ticketNo']} dueAt={data.get('dueAt')[:19]}"

    check("客户提交售后工单 -> SUBMITTED + 订单摘要 PROCESSING", create_ticket)

    def operator_accepts() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/after-sales/{state['ticket_no']}/accept",
            None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        expect(payload["data"]["status"] == "PROCESSING", f"status={payload['data']['status']}")
        return "工单 PROCESSING"

    check("运营受理 -> PROCESSING", operator_accepts)

    def operator_pend_worker() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/after-sales/{state['ticket_no']}/pending",
            {"toStatus": "PENDING_WORKER", "reason": "请人员说明迟到原因"}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["status"] == "PENDING_WORKER",
               f"status={payload['data']['status']}")
        return "工单 PENDING_WORKER"

    check("转待人员说明 -> PENDING_WORKER", operator_pend_worker)

    def worker_statement() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/worker/after-sales/{state['ticket_no']}/statement",
            {"message": "当天交通堵塞，已提前报备"}, token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        expect(payload["data"]["status"] == "PROCESSING",
               f"status={payload['data']['status']}")
        events = payload["data"].get("events") or []
        expect(any(e["eventType"] == "WORKER_STATEMENT" for e in events),
               "时间线缺少 WORKER_STATEMENT 事件")
        return "人员陈述 + 时间线事件"

    check("人员陈述 -> 回到 PROCESSING（含时间线）", worker_statement)

    def resolve_partial_refund() -> str:
        half = (state["payment_amount"] / 2).quantize(Decimal("0.01"))
        state["refund_amount"] = half
        status, payload = api.call(
            "POST", f"/api/v1/operator/after-sales/{state['ticket_no']}/resolve",
            {"resolutionType": "PARTIAL_REFUND", "resolutionText": "核实迟到属实，退还半价",
             "responsibility": "WORKER", "refundAmount": str(half)}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["status"] == "RESOLVED", f"status={data['status']}")
        expect(data.get("refundId"), "退款结论应回填 refundId")
        state["refund_id"] = data["refundId"]
        events = data.get("events") or []
        expect(any(e["eventType"] == "REFUND_LINKED" for e in events),
               "时间线缺少 REFUND_LINKED 事件")
        return f"工单 RESOLVED + refundId={data['refundId']}"

    check("形成结论（部分退款）-> RESOLVED + 退款单创建", resolve_partial_refund)

    def refund_pending_approval() -> str:
        status, payload = api.call("GET", "/api/v1/operator/refunds?page=1&size=50",
                                   token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        records = (payload.get("data") or {}).get("records") or []
        mine = [r for r in records
                if Decimal(str(r.get("amount") or 0)) == state["refund_amount"]]
        expect(len(mine) >= 1, "未找到部分退款金额对应的退款申请")
        target = mine[0]
        expect(target["status"] == "REQUESTED", f"退款申请状态 {target['status']}")
        state["refund_no"] = target["refundNo"]
        return f"refundNo={state['refund_no']} status=REQUESTED"

    check("退款申请待审批（支付模块 REQUESTED）", refund_pending_approval)

    def approve_refund() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/refunds/{state['refund_no']}/approve",
            None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        expect(payload["data"]["status"] == "SUCCESS", f"status={payload['data']['status']}")
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o1']}",
                                   token=state["tokenA"])
        data = payload["data"]
        refunded = Decimal(str(data.get("refundedAmount") or 0))
        expect(refunded == state["refund_amount"],
               f"订单已退金额 {refunded} 应等于 {state['refund_amount']}")
        return f"退款 SUCCESS + 订单已退 {refunded}"

    check("运营审批退款 -> 网关执行 SUCCESS + 订单摘要累加", approve_refund)

    def close_ticket() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/after-sales/{state['ticket_no']}/close",
            None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["status"] == "CLOSED", f"status={payload['data']['status']}")
        status, payload = api.call("GET", f"/api/v1/customer/bookings/{state['o1']}",
                                   token=state["tokenA"])
        expect(payload["data"]["afterSaleStatus"] == "CLOSED",
               f"订单售后摘要应为 CLOSED，实际 {payload['data']['afterSaleStatus']}")
        return "工单 CLOSED + 订单摘要 CLOSED"

    check("关闭工单 -> CLOSED", close_ticket)

    def quality_after_complaint() -> str:
        status, payload = api.call("GET", "/api/v1/worker/reviews/summary",
                                   token=state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data.get("lateCount") == 1, f"lateCount={data.get('lateCount')}（LATE_ARRIVAL 工单）")
        return (f"lateCount={data.get('lateCount')} complaintCount={data.get('complaintCount')} "
                f"completedOrderCount={data.get('completedOrderCount')}")

    check("售后结论后质量汇总更新 -> late_count=1", quality_after_complaint)

    def recalculate_all() -> str:
        status, payload = api.call("POST", "/api/v1/operator/quality/recalculate",
                                   None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        return "全量重算完成"

    check("运营手动全量重算质量汇总", recalculate_all)

    # ================================================================ 汇总
    print()
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项。")
    if FAILED:
        print("失败清单：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("批次 3 第二轮冒烟验证全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
