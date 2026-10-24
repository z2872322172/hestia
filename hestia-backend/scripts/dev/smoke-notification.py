#!/usr/bin/env python3
"""批次 5 通知模块冒烟验证：任务创建幂等、模板渲染发送、已读与越权、退避重试。

验证的核心命题
--------------
**通知是业务的"至少一次投递 + 幂等收敛"副产物**：同一事件重复消费只落一条
任务（dedupe_key 唯一索引兜底）；任务到期渲染模板落站内消息（task_id 唯一
索引防重复投递）；发送失败按 1/5/30/120 分钟退避重试，五次耗尽进 FAILED 等
人工处理；收件箱数据按账号收敛——别人的通知编号探测一律"不存在"。

前提
----
1. 应用已启动（local profile），V8 迁移已执行；
2. ``app.security.smoke-api-enabled=true``（smoke 端点免登录）；
3. 库里存在已授 ``OPERATOR`` 角色的账号。

隔离策略
--------
通知任务按 sourceEventId 隔离：每次运行生成唯一事件 ID，脏库可重跑；同
sourceEventId 重复注入返回空列表正好是幂等断言。

安全约定
--------
只打印令牌前 8 位与脱敏手机号。

用法
----
    python scripts/dev/smoke-notification.py
    python scripts/dev/smoke-notification.py --base-url http://127.0.0.1:8080
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta


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
    return prefix + str(int(time.time() * 1000) % 100000000).zfill(8)


def iso(moment: datetime) -> str:
    return moment.strftime("%Y-%m-%dT%H:%M:%S")


def main() -> int:
    parser = argparse.ArgumentParser(description="批次 5 通知模块冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--operator-phone", default="13900000001")
    parser.add_argument("--operator-password", default="Hestia2026")
    parser.add_argument("--customer-password", default="Hestia2026")
    parser.add_argument("--paid-template", default="NTPL_BKG_PAID_CUS")
    parser.add_argument("--completed-template", default="NTPL_BKG_COMPLETED_CUS")
    args = parser.parse_args()

    api = Api(args.base_url)
    ts = int(time.time())
    phone_a = new_phone("136")
    phone_b = new_phone("137")
    service_date = (datetime(2026, 12, 1) + timedelta(days=ts % 60)).date().isoformat()
    service_start = f"{service_date}T09:00:00"
    # 事件 ID / 订单号按运行时刻隔离，脏库可重跑
    event_paid = f"SMOKE-NTF-PAID-{ts}"
    event_created = f"SMOKE-NTF-CREATED-{ts}"
    event_completed = f"SMOKE-NTF-COMPLETED-{ts}"
    order_paid = f"SMK{ts}P"
    order_created = f"SMK{ts}C"
    order_completed = f"SMK{ts}D"
    state: dict = {}

    print("=" * 78)
    print("批次 5 · 通知模块冒烟验证")
    print(f"接口地址：{args.base_url}")
    print(f"运营账号：{mask_phone(args.operator_phone)}")
    print(f"客户A：{mask_phone(phone_a)}  客户B：{mask_phone(phone_b)}")
    print(f"服务日期：{service_date}")
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

    def prepare_customers() -> str:
        for key, phone in (("token_a", phone_a), ("token_b", phone_b)):
            status, payload = api.call("POST", "/api/v1/auth/customer/register", {
                "countryCode": "+86", "phone": phone,
                "password": args.customer_password, "displayName": "通知冒烟客户"})
            expect(status == 200,
                   f"注册 {phone} -> HTTP {status} code={payload.get('code')}")
            state[key] = payload["data"]["accessToken"]
        status, payload = api.call("GET", "/api/v1/customer/profile", token=state["token_a"])
        expect(status == 200, f"查客户档案 -> HTTP {status} code={payload.get('code')}")
        state["customer_no"] = payload["data"]["customerNo"]
        return f"客户A 编号 {state['customer_no']}"

    check("准备：注册两个客户并取客户编号", prepare_customers)

    # ================================================================ 1. 任务创建：收件人解析与幂等

    def ingest_event(event_id: str, event_type: str, order_no: str,
                     payload_extra: dict | None = None) -> list[str]:
        body = {"eventType": event_type, "sourceEventId": event_id,
                "orderNo": order_no, "payload": payload_extra or {}}
        status, payload = api.call(
            "POST", "/api/v1/system/smoke/notification/ingest", body)
        expect(status == 200, f"ingest -> HTTP {status} code={payload.get('code')}")
        return payload["data"]["taskNos"]

    def create_paid_task() -> str:
        task_nos = ingest_event(event_paid, "BOOKING_PAID", order_paid, {
            "customerNo": state["customer_no"],
            "appointmentDate": service_date,
            "appointmentStart": service_start,
        })
        expect(len(task_nos) == 1, f"客户侧事件应建 1 个任务，实际 {len(task_nos)}")
        state["paid_task_no"] = task_nos[0]
        return task_nos[0]

    check("支付事件按客户编号建任务", create_paid_task)

    def dedupe_replay() -> str:
        task_nos = ingest_event(event_paid, "BOOKING_PAID", order_paid, {
            "customerNo": state["customer_no"],
            "appointmentDate": service_date,
            "appointmentStart": service_start,
        })
        expect(task_nos == [], f"同事件重放应命中去重返回空列表，实际 {task_nos}")
        return "重放返回 0 任务"

    check("同一事件重复消费幂等去重", dedupe_replay)

    def edge_ingests() -> str:
        empty = ingest_event(f"SMOKE-NTF-NORCP-{ts}", "BOOKING_PAID", order_paid, {})
        expect(empty == [], "无收件人事件应静默返回空列表")
        unknown = ingest_event(f"SMOKE-NTF-UNKN-{ts}", "SOMETHING_ELSE", order_paid,
                               {"customerNo": state["customer_no"]})
        expect(unknown == [], "未登记事件类型应静默返回空列表")
        return "无收件人 / 未登记事件均不报错"

    check("无收件人与未登记事件不报错", edge_ingests)

    # ================================================================ 2. 发送与收件箱

    def dispatch(as_of: datetime | None = None) -> int:
        path = "/api/v1/system/smoke/notification/dispatch"
        if as_of is not None:
            path += f"?asOf={iso(as_of)}"
        status, payload = api.call("POST", path)
        expect(status == 200, f"dispatch -> HTTP {status} code={payload.get('code')}")
        return payload["data"]["handled"]

    def dispatch_paid() -> str:
        # asOf 取未来 1 分钟：任务 scheduled_at 是服务端时钟，留余量避免漏扫
        handled = dispatch(datetime.now() + timedelta(minutes=1))
        expect(handled >= 1, f"发送扫描应至少处理 1 个任务，实际 handled={handled}")
        return f"本轮处理 {handled} 个任务"

    check("发送扫描推进任务", dispatch_paid)

    def find_notification(token: str, order_no: str) -> dict:
        status, payload = api.call(
            "GET", "/api/v1/notifications?page=1&size=50", token=token)
        expect(status == 200, f"收件箱 -> HTTP {status} code={payload.get('code')}")
        for record in payload["data"]["records"]:
            if record["businessId"] == order_no:
                return record
        raise Failure(f"收件箱缺订单 {order_no} 的通知")

    def inbox_has_paid() -> str:
        record = find_notification(state["token_a"], order_paid)
        expect(order_paid in record["content"], f"正文应含订单号，实际：{record['content']}")
        expect(record["readAt"] is None, "新通知应为未读")
        state["paid_notification_no"] = record["notificationNo"]
        return f"{record['notificationNo']}：{record['title']}"

    check("客户A 收到支付成功通知", inbox_has_paid)

    def unread_flow() -> str:
        status, payload = api.call("GET", "/api/v1/notifications/unread-count",
                                   token=state["token_a"])
        expect(status == 200, f"未读数 -> HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["unread"] == 1, f"未读应为 1，实际 {payload['data']['unread']}")
        no = state["paid_notification_no"]
        for _ in range(2):  # 第二次重复标记验证幂等
            status, payload = api.call("PUT", f"/api/v1/notifications/{no}/read",
                                       token=state["token_a"])
            expect(status == 200, f"标记已读 -> HTTP {status} code={payload.get('code')}")
        status, payload = api.call("GET", "/api/v1/notifications/unread-count",
                                   token=state["token_a"])
        expect(payload["data"]["unread"] == 0,
               f"已读后未读应为 0，实际 {payload['data']['unread']}")
        return "未读 1 -> 标记已读（含重复） -> 0"

    check("未读数与标记已读幂等", unread_flow)

    def cross_customer_denied() -> str:
        no = state["paid_notification_no"]
        status, payload = api.call("GET", "/api/v1/notifications?page=1&size=50",
                                   token=state["token_b"])
        expect(status == 200, f"客户B 收件箱 -> HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["records"] == [], "客户B 不应看到任何通知")
        return expect_error(api, "PUT", f"/api/v1/notifications/{no}/read",
                            404, "NOTIFICATION_NOT_OWNED", token=state["token_b"]) \
            + "（客户B 探测他人通知）"

    check("越权标记他人通知回不存在", cross_customer_denied)

    def read_all_flow() -> str:
        ingest_event(event_created, "BOOKING_CREATED", order_created,
                     {"customerNo": state["customer_no"]})
        dispatch(datetime.now() + timedelta(minutes=1))
        find_notification(state["token_a"], order_created)  # 到位即可
        status, payload = api.call("PUT", "/api/v1/notifications/read-all",
                                   token=state["token_a"])
        expect(status == 200, f"全部已读 -> HTTP {status} code={payload.get('code')}")
        marked = payload["data"]["marked"]
        expect(marked >= 1, f"应至少标记 1 条，实际 {marked}")
        status, payload = api.call("GET", "/api/v1/notifications/unread-count",
                                   token=state["token_a"])
        expect(payload["data"]["unread"] == 0,
               f"全部已读后未读应为 0，实际 {payload['data']['unread']}")
        return f"标记 {marked} 条"

    check("下单通知与全部已读", read_all_flow)

    # ================================================================ 3. 失败退避与人工重试

    def disable_template(code: str) -> None:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/notification-templates/{code}",
            {"titleTemplate": "服务已完成", "contentTemplate": "订单 {orderNo} 的服务已完成。",
             "status": "DISABLED"}, operator_token)
        expect(status == 200, f"停用模板 {code} -> HTTP {status} code={payload.get('code')}")

    def enable_template(code: str) -> None:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/notification-templates/{code}",
            {"titleTemplate": "服务已完成",
             "contentTemplate": "订单 {orderNo} 的服务已完成，欢迎对本次服务进行评价。",
             "status": "ACTIVE"}, operator_token)
        expect(status == 200, f"启用模板 {code} -> HTTP {status} code={payload.get('code')}")

    def find_task(status_filter: str, source_event_id: str) -> dict:
        status, payload = api.call(
            "GET", f"/api/v1/operator/notification-tasks?status={status_filter}&page=1&size=100",
            token=operator_token)
        expect(status == 200, f"任务列表 -> HTTP {status} code={payload.get('code')}")
        for record in payload["data"]["records"]:
            if record["sourceEventId"] == source_event_id:
                return record
        raise Failure(f"任务列表（{status_filter}）缺事件 {source_event_id}")

    def completed_task_fails_until_terminal() -> str:
        # 先建任务（模板此时可用），再停用模板制造"发送阶段模板缺失"的失败
        task_nos = ingest_event(event_completed, "BOOKING_COMPLETED", order_completed,
                                {"customerNo": state["customer_no"]})
        expect(len(task_nos) == 1, f"完成事件应建 1 个任务，实际 {len(task_nos)}")
        state["completed_task_no"] = task_nos[0]
        disable_template(args.completed_template)

        # asOf 依次跨过 1/5/30/120 分钟退避窗，五次失败进 FAILED
        t0 = datetime.now() + timedelta(minutes=1)
        for round_no, offset in enumerate((0, 2, 8, 40, 185), start=1):
            dispatch(t0 + timedelta(minutes=offset))
            record = find_task("FAILED" if round_no == 5 else "PENDING",
                               event_completed)
            expect(record["taskNo"] == state["completed_task_no"],
                   f"任务编号应保持 {state['completed_task_no']}，实际 {record['taskNo']}")
            expect(record["retryCount"] == round_no,
                   f"第 {round_no} 次失败后 retryCount 应为 {round_no}，"
                   f"实际 {record['retryCount']}")
            if round_no < 5:
                expect(record["nextRetryAt"] is not None,
                       f"第 {round_no} 次失败后应有退避时刻")
            else:
                expect("模板" in (record["lastError"] or ""),
                       f"终态错误应含模板原因，实际：{record['lastError']}")
        return f"五次失败退避后 {state['completed_task_no']} -> FAILED"

    check("退避重试五次耗尽进 FAILED", completed_task_fails_until_terminal)

    def retry_after_fix() -> str:
        # 运营先修复（启用模板）再重试：FAILED → PENDING → 立即参与调度
        enable_template(args.completed_template)
        status, payload = api.call(
            "POST", f"/api/v1/operator/notification-tasks/{state['completed_task_no']}/retry",
            None, operator_token)
        expect(status == 200, f"人工重试 -> HTTP {status} code={payload.get('code')}")
        view = payload["data"]
        expect(view["status"] == "PENDING", f"重试后应 PENDING，实际 {view['status']}")
        expect(view["nextRetryAt"] is None and view["lastError"] is None,
               "重试应清空退避与错误信息")
        dispatch(datetime.now() + timedelta(minutes=1))
        record = find_task("SENT", event_completed)
        expect(record["taskNo"] == state["completed_task_no"],
               "重试后任务应被发送")
        find_notification(state["token_a"], order_completed)
        return f"{state['completed_task_no']} -> SENT，客户A 收到完成通知"

    check("人工修复后重试成功", retry_after_fix)

    # ================================================================ 4. 模板维护

    def templates_inventory() -> str:
        status, payload = api.call("GET", "/api/v1/operator/notification-templates",
                                   token=operator_token)
        expect(status == 200, f"模板列表 -> HTTP {status} code={payload.get('code')}")
        codes = {t["templateCode"] for t in payload["data"]}
        expect(len(codes) >= 8, f"种子模板应至少 8 条，实际 {len(codes)}")
        state["templates"] = {t["templateCode"]: t for t in payload["data"]}
        return f"{len(codes)} 条模板"

    check("种子模板清单完整", templates_inventory)

    def sensitive_variable_rejected() -> str:
        return expect_error(
            api, "PUT", f"/api/v1/operator/notification-templates/{args.paid_template}",
            400, "VALIDATION_FAILED",
            {"titleTemplate": "支付成功", "contentTemplate": "订单 {orderNo} 手机 {phone}",
             "status": "ACTIVE"}, operator_token) + "（敏感变量 {phone} 被拦截）"

    check("模板引入敏感变量被拒绝", sensitive_variable_rejected)

    def template_version_bump() -> str:
        code = args.paid_template
        original = state["templates"][code]
        status, payload = api.call(
            "PUT", f"/api/v1/operator/notification-templates/{code}",
            {"titleTemplate": original["titleTemplate"],
             "contentTemplate": "订单 {orderNo} 支付成功，服务安排在 {serviceDate} {startTime}。",
             "status": original["status"]}, operator_token)
        expect(status == 200, f"修改模板 -> HTTP {status} code={payload.get('code')}")
        updated = payload["data"]
        expect(updated["version"] == original["version"] + 1,
               f"内容版本应 +1（{original['version']} -> {updated['version']}）")
        status, payload = api.call(
            "PUT", f"/api/v1/operator/notification-templates/{code}",
            {"titleTemplate": original["titleTemplate"],
             "contentTemplate": original["contentTemplate"],
             "status": original["status"]}, operator_token)
        expect(status == 200, f"恢复模板 -> HTTP {status} code={payload.get('code')}")
        return f"{code} v{original['version']} -> v{updated['version']} -> 已恢复"

    check("模板正常修改版本递增并恢复", template_version_bump)

    # ---------------------------------------------------------------- 汇总
    print("=" * 78)
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项")
    if FAILED:
        print("失败项：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("通知链路全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
