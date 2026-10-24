#!/usr/bin/env python3
"""模块 13（运营 / 统计与审计）冒烟验证：工作台 → 监控 → 报表 → 日报 → 导出 → 配置 → 审计。

验证的核心命题
--------------
1. 运营工作台与监控接口按授权区域聚合各模块统计契约，无区域配置时拒绝；
2. 日报读模型由冒烟接口驱动重算，按 (metric_date, service_area_id) UPSERT
   幂等——重复执行两次的行数一致，且 /reports/daily 能读到；
3. 数据导出：仅 ORDERS 可发起 → PENDING → 冒烟 run-once 生成文件 → SUCCESS
   → 短期签名 URL 下载 → 终态取消被拒；
4. 系统配置修改走乐观锁：版本冲突、类型非法均被拒，成功后审计可查
   （CONFIG_UPDATE），审计日志只读可分页。

前提
----
1. 应用已启动（local profile），V1~V9 迁移已执行；
2. 库里存在已授 ``OPERATOR`` 角色的账号（smoke-batch2/3 使用的账号即可）；
3. admin 段检查需要 ``ADMIN`` 角色：优先用 --admin-phone 登录，
   不传则尝试复用运营令牌，无 ADMIN 权限时自动跳过该段。

用法
----
    python scripts/dev/smoke-operations.py
    python scripts/dev/smoke-operations.py --base-url http://127.0.0.1:18080 \
        --admin-phone 13900000000
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta

DEFAULT_EXPORT_DAYS = 7


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
                raw = response.read().decode("utf-8")
                return response.status, json.loads(raw)
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


def expect_error(api: Api, method: str, path: str, expected_status: int,
                 expected_code: str, body: dict | None = None,
                 token: str | None = None) -> str:
    status, payload = api.call(method, path, body, token)
    expect(status == expected_status,
           f"期望 HTTP {expected_status}，实际 {status}（code={payload.get('code')}）")
    expect(payload.get("code") == expected_code,
           f"期望错误码 {expected_code}，实际 {payload.get('code')}")
    return f"{status}/{expected_code}"


def mask(token: str | None) -> str:
    if not token:
        return "<空>"
    return token[:8] + "…"


def main() -> int:
    parser = argparse.ArgumentParser(description="模块 13 运营统计与审计冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--operator-phone", default="13900000001")
    parser.add_argument("--operator-password", default="Hestia2026")
    parser.add_argument("--admin-phone", default=None,
                        help="已授予 ADMIN 角色的手机号；不传则尝试复用运营令牌")
    parser.add_argument("--admin-password", default="Hestia2026")
    args = parser.parse_args()

    api = Api(args.base_url)
    today = date.today()
    iso = lambda d: d.isoformat()  # noqa: E731
    ts = int(time.time())
    state: dict = {}

    print("=" * 78)
    print("模块 13 · 运营工作台 / 报表 / 日报 / 导出 / 配置 / 审计 冒烟验证")
    print(f"接口地址：{args.base_url}")
    print(f"运营账号：{args.operator_phone[:3]}****{args.operator_phone[-4:]}")
    print("=" * 78)

    # ---------------------------------------------------------------- 登录
    status, payload = api.call("POST", "/api/v1/auth/login", {
        "phone": args.operator_phone, "password": args.operator_password})
    if status != 200:
        print(f"[中止] 运营账号登录失败：HTTP {status} code={payload.get('code')}")
        return 2
    operator_token = payload["data"]["accessToken"]
    state["token"] = operator_token
    print(f"运营令牌：{mask(operator_token)}\n")

    # ================================================================ 1. 工作台
    def dashboard_summary() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/dashboard/summary?date={iso(today)}",
            token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        for field in ("created", "pendingPayment", "pendingAccept", "manualRequired",
                      "inService", "exceptionCount", "pendingRefund", "openTickets",
                      "expiringCertificates"):
            expect(field in data, f"汇总缺少字段 {field}")
            expect(isinstance(data[field], int), f"{field} 应为整数，实际 {data[field]!r}")
        return (f"今日下单 {data['created']} 待支付 {data['pendingPayment']} "
                f"服务中 {data['inService']} 待人工 {data['manualRequired']}")

    check("工作台汇总（dashboard/summary）", dashboard_summary)

    def dashboard_alerts() -> str:
        status, payload = api.call("GET", "/api/v1/operator/dashboard/alerts",
                                   token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        alerts = payload["data"] or []
        for alert in alerts:
            expect(alert.get("type"), "告警缺少 type")
            expect(alert.get("level") in ("HIGH", "MEDIUM"), f"告警级别 {alert.get('level')}")
            expect(alert.get("count", 0) > 0, "告警只应返回数量大于 0 的项")
        return f"告警 {len(alerts)} 条"

    check("工作台告警（dashboard/alerts）", dashboard_alerts)

    # ================================================================ 2. 监控
    def orders_monitor() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/orders/monitor?page=1&size=5"
                   f"&appointmentDate={iso(today)}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        for field in ("records", "page", "size", "total"):
            expect(field in data, f"分页缺少字段 {field}")
        return f"total={data['total']} 本页 {len(data['records'])} 条"

    check("订单监控（orders/monitor）", orders_monitor)

    def dispatch_monitor() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/dispatch/monitor?date={iso(today)}",
            token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        for field in ("pendingAccept", "manualRequired", "autoSuccess", "rejected",
                      "timeout", "manual"):
            expect(field in data, f"派单监控缺少字段 {field}")
        return (f"待接单 {data['pendingAccept']} 待人工 {data['manualRequired']} "
                f"拒单 {data['rejected']}")

    check("派单监控（dispatch/monitor）", dispatch_monitor)

    def fulfillment_monitor() -> str:
        status, payload = api.call("GET", "/api/v1/operator/fulfillment/monitor",
                                   token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        for field in ("pending", "enRoute", "checkedIn", "inService", "pendingConfirm",
                      "exceptionCount"):
            expect(field in data, f"履约监控缺少字段 {field}")
        return f"服务中 {data['inService']} 待确认 {data['pendingConfirm']} 异常 {data['exceptionCount']}"

    check("履约监控（fulfillment/monitor）", fulfillment_monitor)

    # ================================================================ 3. 实时报表
    def reports_orders() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/reports/orders?from={iso(today - timedelta(days=7))}"
                   f"&to={iso(today)}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        rows = payload["data"] or []
        for row in rows:
            expect({"statDate", "serviceAreaId", "created", "completed", "canceled"}
                   <= set(row), f"订单报表行缺字段：{row}")
        return f"行数 {len(rows)}"

    check("订单报表（reports/orders）", reports_orders)

    def reports_payments() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/reports/payments?from={iso(today - timedelta(days=7))}"
                   f"&to={iso(today)}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        rows = payload["data"] or []
        for row in rows:
            expect({"statDate", "paidCount", "paidAmount", "refundAmount"} <= set(row),
                   f"支付报表行缺字段：{row}")
        return f"行数 {len(rows)}"

    check("支付报表（reports/payments）", reports_payments)

    def reports_workers() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/reports/workers?from={iso(today - timedelta(days=7))}"
                   f"&to={iso(today)}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        rows = payload["data"] or []
        for row in rows:
            expect({"statDate", "slotTotal", "slotBooked", "utilizationPercent"} <= set(row),
                   f"人员报表行缺字段：{row}")
        return f"行数 {len(rows)}"

    check("人员时段报表（reports/workers）", reports_workers)

    def reports_after_sales() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/reports/after-sales?from={iso(today - timedelta(days=7))}"
                   f"&to={iso(today)}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        rows = payload["data"] or []
        for row in rows:
            expect({"statDate", "complaintCount", "reworkCount"} <= set(row),
                   f"售后报表行缺字段：{row}")
        return f"行数 {len(rows)}"

    check("售后报表（reports/after-sales）", reports_after_sales)

    def report_range_invalid() -> str:
        return expect_error(api, "GET",
                            f"/api/v1/operator/reports/orders?from={iso(today)}"
                            f"&to={iso(today - timedelta(days=1))}",
                            400, "REPORT_RANGE_INVALID", token=operator_token)

    check("报表区间倒置 -> 400 REPORT_RANGE_INVALID", report_range_invalid)

    def report_range_too_long() -> str:
        return expect_error(api, "GET",
                            f"/api/v1/operator/reports/orders?from={iso(today - timedelta(days=93))}"
                            f"&to={iso(today)}",
                            400, "REPORT_RANGE_INVALID", token=operator_token)

    check("报表区间超 92 天 -> 400 REPORT_RANGE_INVALID", report_range_too_long)

    # ================================================================ 4. 日报读模型（幂等重算）
    def recalc_daily_first() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/system/smoke/operations/daily-metric?date={iso(today)}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["daily_rows_first"] = payload["data"]
        return f"UPSERT {state['daily_rows_first']} 行"

    check("日报重算（第 1 次，冒烟驱动）", recalc_daily_first)

    def recalc_daily_second_idempotent() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/system/smoke/operations/daily-metric?date={iso(today)}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        rows_second = payload["data"]
        expect(rows_second == state["daily_rows_first"],
               f"重复执行行数应一致：第 1 次 {state['daily_rows_first']}，"
               f"第 2 次 {rows_second}")
        return f"UPSERT {rows_second} 行（与第 1 次一致 → 幂等）"

    check("日报重算（第 2 次）→ 幂等", recalc_daily_second_idempotent)

    def daily_report_readable() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/reports/daily?from={iso(today)}&to={iso(today)}"
                   f"&page=1&size=50", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        rows = data.get("records") or []
        expect(data.get("total", 0) >= state["daily_rows_first"],
               f"日报 total={data.get('total')} 应不少于重算行数 {state['daily_rows_first']}")
        if rows:
            row = rows[0]
            expect({"metricDate", "serviceAreaId", "ordersCreated", "metricVersion",
                    "recalcCount"} <= set(row), f"日报行缺字段：{row}")
            expect(row.get("recalcCount", 0) >= 1,
                   f"重算次数应 >= 1，实际 {row.get('recalcCount')}")
        return f"total={data.get('total')}，最新行 recalcCount={rows[0].get('recalcCount') if rows else '-'}"

    check("日报读模型可查（reports/daily）", daily_report_readable)

    # ================================================================ 5. 数据导出
    def export_forbidden_type() -> str:
        return expect_error(api, "POST", "/api/v1/operator/exports",
                            403, "REPORT_EXPORT_NOT_ALLOWED",
                            {"exportType": "WORKERS", "from": iso(today - timedelta(days=1)),
                             "to": iso(today), "reason": "冒烟：不允许的类型"},
                            token=operator_token)

    check("导出未开放类型 WORKERS -> 403 REPORT_EXPORT_NOT_ALLOWED", export_forbidden_type)

    def export_range_invalid() -> str:
        return expect_error(api, "POST", "/api/v1/operator/exports",
                            400, "REPORT_RANGE_INVALID",
                            {"exportType": "ORDERS", "from": iso(today),
                             "to": iso(today - timedelta(days=1))},
                            token=operator_token)

    check("导出区间倒置 -> 400 REPORT_RANGE_INVALID", export_range_invalid)

    def export_create() -> str:
        status, payload = api.call("POST", "/api/v1/operator/exports",
                                   {"exportType": "ORDERS",
                                    "from": iso(today - timedelta(days=DEFAULT_EXPORT_DAYS)),
                                    "to": iso(today),
                                    "reason": "模块 13 冒烟导出"},
                                   token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["exportType"] == "ORDERS", f"exportType={data['exportType']}")
        expect(data["status"] == "PENDING", f"status={data['status']}")
        expect(bool(data.get("exportNo")) and data["exportNo"].startswith("EXP"),
               f"exportNo={data.get('exportNo')}")
        state["export_no"] = data["exportNo"]
        return f"exportNo={data['exportNo']} PENDING"

    check("发起订单导出 -> PENDING", export_create)

    def export_detail_pending() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/exports/{state['export_no']}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["status"] in ("PENDING", "PROCESSING"),
               f"处理前状态 {payload['data']['status']}")
        return f"status={payload['data']['status']}"

    check("导出详情（发起人可见）", export_detail_pending)

    def export_download_before_ready() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/exports/{state['export_no']}/download-url", None,
            token=operator_token)
        expect(status == 409, f"未生成文件应 409，实际 {status} code={payload.get('code')}")
        expect(payload.get("code") == "EXPORT_FILE_NOT_READY",
               f"错误码 {payload.get('code')}")
        return "409/EXPORT_FILE_NOT_READY"

    check("未生成文件时下载 -> 409 EXPORT_FILE_NOT_READY", export_download_before_ready)

    def export_run_once() -> str:
        status, payload = api.call(
            "POST", "/api/v1/system/smoke/operations/exports/run-once?batchSize=5")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        processed = payload["data"]
        expect(processed >= 1, f"应处理至少 1 个任务，实际 {processed}")
        return f"处理 {processed} 个任务"

    check("冒烟驱动导出任务处理", export_run_once)

    def export_download_url() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/operator/exports/{state['export_no']}", token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        data = payload["data"]
        expect(data["status"] == "SUCCESS", f"处理后状态 {data['status']}")
        expect(data.get("rowCount") is not None, "成功后应回填 row_count")
        status, payload = api.call(
            "POST", f"/api/v1/operator/exports/{state['export_no']}/download-url", None,
            token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        url = payload["data"]["url"]
        expect(url.startswith("http"), f"下载地址异常：{url[:60]}")
        expect(payload["data"].get("expiresInSeconds", 0) > 0, "应返回有效期")
        return f"SUCCESS rows={data['rowCount']} url 有效期 {payload['data']['expiresInSeconds']}s"

    check("导出成功 + 短期签名 URL 可取", export_download_url)

    def export_cancel_terminal_rejected() -> str:
        return expect_error(api, "POST",
                            f"/api/v1/operator/exports/{state['export_no']}/cancel",
                            409, "OPERATION_NOT_ALLOWED", None, token=operator_token)

    check("终态导出取消 -> 409 OPERATION_NOT_ALLOWED", export_cancel_terminal_rejected)

    def export_list_visible() -> str:
        status, payload = api.call("GET", "/api/v1/operator/exports?page=1&size=10",
                                   token=operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        records = (payload.get("data") or {}).get("records") or []
        expect(any(r.get("exportNo") == state["export_no"] for r in records),
               "导出列表应包含本次任务")
        return f"本页 {len(records)} 条"

    check("导出任务列表包含本次任务", export_list_visible)

    # ================================================================ 6. 系统配置与审计（需 ADMIN）
    admin_token: str | None = None
    if args.admin_phone:
        status, payload = api.call("POST", "/api/v1/auth/login", {
            "phone": args.admin_phone, "password": args.admin_password})
        if status == 200:
            admin_token = payload["data"]["accessToken"]
    if admin_token is None:
        # 尝试复用运营令牌（本地库常把 OPERATOR / ADMIN 给同一账号）
        status, payload = api.call("GET", "/api/v1/admin/system-configs", token=operator_token)
        if status == 200:
            admin_token = operator_token

    if admin_token is None:
        print("[跳过] 系统配置与审计段：无 ADMIN 角色账号（--admin-phone 可指定）")
    else:
        def config_list() -> str:
            status, payload = api.call("GET", "/api/v1/admin/system-configs",
                                       token=admin_token)
            expect(status == 200, f"HTTP {status} code={payload.get('code')}")
            configs = payload["data"] or []
            expect(len(configs) >= 10, f"种子配置应至少 10 条，实际 {len(configs)}")
            target = next((c for c in configs
                           if c["configKey"] == "certificate_expiry_notice_days"), None)
            expect(target is not None, "缺少 certificate_expiry_notice_days")
            state["config_version"] = target["version"]
            return f"配置 {len(configs)} 条，目标版本 v{target['version']}"

        check("配置列表（10 条种子）", config_list)

        def config_update_type_invalid() -> str:
            return expect_error(api, "PUT",
                                "/api/v1/admin/system-configs/certificate_expiry_notice_days",
                                400, "SYSTEM_CONFIG_TYPE_INVALID",
                                {"value": "abc", "reason": "冒烟：非法类型",
                                 "expectedVersion": state["config_version"]},
                                token=admin_token)

        check("配置值类型非法 -> 400 SYSTEM_CONFIG_TYPE_INVALID", config_update_type_invalid)

        def config_update_stale_version() -> str:
            return expect_error(api, "PUT",
                                "/api/v1/admin/system-configs/certificate_expiry_notice_days",
                                409, "SYSTEM_CONFIG_VERSION_CONFLICT",
                                {"value": "21", "reason": "冒烟：旧版本提交",
                                 "expectedVersion": state["config_version"] - 1},
                                token=admin_token)

        check("旧版本提交 -> 409 SYSTEM_CONFIG_VERSION_CONFLICT", config_update_stale_version)

        def config_update_success() -> str:
            status, payload = api.call(
                "PUT", "/api/v1/admin/system-configs/certificate_expiry_notice_days",
                {"value": "21", "reason": "模块 13 冒烟：到期提醒调为 21 天",
                 "expectedVersion": state["config_version"]},
                token=admin_token)
            expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
            data = payload["data"]
            expect(data["value"] == "21", f"新值 {data['value']}")
            expect(data["version"] == state["config_version"] + 1,
                   f"版本应 +1：{data['version']}")
            return f"新值 21 版本 v{data['version']}"

        check("合法修改 -> 值生效 + 版本 +1", config_update_success)

        def audit_has_config_update() -> str:
            status, payload = api.call(
                "GET", "/api/v1/admin/audit-logs?actionType=CONFIG_UPDATE&page=1&size=10",
                token=admin_token)
            expect(status == 200, f"HTTP {status} code={payload.get('code')}")
            records = (payload.get("data") or {}).get("records") or []
            target = next((r for r in records
                           if r.get("businessId") == "certificate_expiry_notice_days"), None)
            expect(target is not None, "未找到本次 CONFIG_UPDATE 审计")
            expect(target.get("afterJson") and "\"value\":21" in target["afterJson"],
                   f"审计 after 摘要异常：{target.get('afterJson')}")
            state["audit_id"] = target["id"]
            return f"auditId={target['id']} 含 after 摘要"

        check("配置修改审计可查（CONFIG_UPDATE）", audit_has_config_update)

        def audit_detail() -> str:
            status, payload = api.call(
                "GET", f"/api/v1/admin/audit-logs/{state['audit_id']}", token=admin_token)
            expect(status == 200, f"HTTP {status} code={payload.get('code')}")
            data = payload["data"]
            expect(data["actionType"] == "CONFIG_UPDATE", f"actionType={data['actionType']}")
            expect(bool(data.get("operatorAccountId")), "审计缺少操作账号")
            return f"auditId={data['id']} operator={data.get('operatorAccountId')}"

        check("审计详情可查", audit_detail)

        def audit_not_found() -> str:
            return expect_error(api, "GET", "/api/v1/admin/audit-logs/999999999",
                                404, "AUDIT_LOG_NOT_FOUND", token=admin_token)

        check("审计不存在 -> 404 AUDIT_LOG_NOT_FOUND", audit_not_found)

    # ================================================================ 汇总
    print()
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项。")
    if FAILED:
        print("失败清单：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("模块 13 冒烟验证全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
