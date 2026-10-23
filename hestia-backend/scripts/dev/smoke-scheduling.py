#!/usr/bin/env python3
"""批次 2 排班模块冒烟验证：时间窗、排班、请假、停排、可用性聚合。

验证的核心命题
--------------
**可用性是三级过滤后的"有人可约"**：消费者查询某天某时间窗，看到的是
"库存有 AVAILABLE 时段 × 人员没到接单上限 × 技能/证书/区域齐备"的合取结果，
任何一个环节为空都表现为不可约——而不是报错或半截信息。排班变更
（请假/停排/修改）遇到已占用时段必须拒绝，这个约束单测覆盖不了事务路径。

前提
----
1. 应用已启动（local profile），V1~V4 迁移已执行；
2. 库里存在已授 ``OPERATOR`` 角色的账号（同 smoke-workforce.py 前提）。

可用性是全库聚合：脚本使用 5~7 天后（远期）的日期做断言，避免与其他
冒烟或历史数据在近期日期上的排班互相干扰。

安全约定
--------
只打印令牌前 8 位与脱敏手机号。

用法
----
    python scripts/dev/smoke-scheduling.py
    python scripts/dev/smoke-scheduling.py --base-url http://127.0.0.1:8080
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
DEFAULT_AREA_ID = 2


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


def find_clean_dates(api: Api, item_no: str, area_id: int, count: int,
                     start_offset: int = 5) -> list[date]:
    """逐日探测，返回 count 个"全库无遗留排班"的连续可选日期。

    判定标准：该日 MORNING 与 AFTERNOON 都不可约（本种子项目时长放得进
    这两个窗口，若有人排班且时段空闲必为 True）。遇到被遗留数据占用的
    日期直接跳过，保证后续"未排班 -> 不可约"的断言成立。
    """
    clean: list[date] = []
    offset = start_offset
    while len(clean) < count and offset < 400:
        day = date.today() + timedelta(days=offset)
        status, payload = api.call(
            "GET", f"/api/v1/public/availability?serviceItemNo={item_no}"
                   f"&serviceAreaId={area_id}&dateFrom={day.isoformat()}&dateTo={day.isoformat()}")
        if status == 200 and payload.get("data"):
            windows = {w["windowCode"]: w["available"] for w in payload["data"][0]["windows"]}
            if windows.get("MORNING") is False and windows.get("AFTERNOON") is False:
                clean.append(day)
        offset += 1
    expect(len(clean) == count, f"400 天内找不到 {count} 个无排班的干净日期")
    return clean


def main() -> int:
    parser = argparse.ArgumentParser(description="批次 2 排班模块冒烟验证")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
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
    # 可用性是全库聚合：必须用"全库无任何遗留排班"的日期，才能断言
    # "未排班 -> 不可约"。其他冒烟（如订单冒烟）会在远期日期留下排班，
    # 固定偏移早晚会撞上——从 today+5 起逐日探测，挑出干净日期。
    far, far2, far3 = find_clean_dates(api, item_no, area_id, 3)
    iso = lambda d: d.isoformat()  # noqa: E731

    print("=" * 78)
    print("批次 2 · 排班与可用性模块冒烟验证")
    print(f"接口地址：{args.base_url}")
    print(f"运营账号：{mask_phone(args.operator_phone)}")
    print(f"新人员手机号：{mask_phone(worker_phone)}")
    print(f"排班日期：{iso(far)} / {iso(far2)} / {iso(far3)}")
    print("=" * 78)

    # ---------------------------------------------------------------- 前置：运营登录
    status, payload = api.call("POST", "/api/v1/auth/login", {
        "phone": args.operator_phone, "password": args.operator_password})
    if status != 200:
        print(f"[中止] 运营账号登录失败：HTTP {status} code={payload.get('code')}")
        return 2
    operator_token = payload["data"]["accessToken"]
    print(f"运营令牌：{mask(operator_token)}\n")

    state: dict = {}

    # ================================================================ 准备段：一名完全合格的人员
    # 排班本身要求人员"在岗且审核通过"，所以先复刻 workforce 冒烟的准备链路。

    def prepare_worker_create() -> str:
        status, payload = api.call("POST", "/api/v1/operator/workers", {
            "countryCode": "+86", "phone": worker_phone, "displayName": "排班冒烟人员",
            "realName": "排班测试员", "gender": "MALE", "employmentType": "FULL_TIME",
            "experienceYears": 2, "introduction": "排班冒烟创建", "maxDailyOrders": 3,
        }, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        state["worker_no"] = data["workerNo"]
        state["activation_token"] = data["activationToken"]
        return f"workerNo={data['workerNo']}"

    check("准备：创建人员档案并开户", prepare_worker_create)

    def prepare_qualification() -> str:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/service-items/{item_no}/qualification-requirements",
            {"requirements": []}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        return "无资质要求（简化链路）"

    check("准备：清空服务项目资质要求", prepare_qualification)

    def prepare_activate() -> str:
        status, payload = api.call("POST", "/api/v1/auth/worker/activate", {
            "token": state["activation_token"], "password": args.worker_password})
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        state["worker_token"] = payload["data"]["accessToken"]
        return f"token={mask(state['worker_token'])}"

    check("准备：激活人员账号", prepare_activate)

    def prepare_skills_areas_approve() -> str:
        for method, path, body in (
                ("PUT", f"/api/v1/operator/workers/{state['worker_no']}/skills",
                 {"skills": [{"serviceItemNo": item_no, "skillLevel": "INTERMEDIATE",
                              "verified": True}]}),
                ("PUT", f"/api/v1/operator/workers/{state['worker_no']}/service-areas",
                 {"serviceAreaIds": [area_id]}),
        ):
            status, payload = api.call(method, path, body, operator_token)
            expect(status == 200, f"{method} {path} -> HTTP {status} code={payload.get('code')}")
        status, payload = api.call(
            "POST", f"/api/v1/operator/workers/{state['worker_no']}/approve", None, operator_token)
        expect(status == 200, f"approve -> HTTP {status} code={payload.get('code')}")
        expect(payload["data"]["workStatus"] == "ACTIVE", "审核通过应在岗")
        return "技能/区域/审核完成"

    check("准备：技能、区域与审核通过", prepare_skills_areas_approve)

    # ================================================================ 1. 公开时间窗

    def public_windows() -> str:
        status, payload = api.call("GET", "/api/v1/public/service-windows")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        windows = payload["data"]
        codes = [w["windowCode"] for w in windows]
        expect(set(codes) >= {"MORNING", "AFTERNOON", "EVENING"}, f"种子时间窗缺失，实际 {codes}")
        for w in windows:
            state[w["windowCode"]] = w["windowId"]
        expect(not any(w["status"] == "DISABLED" for w in windows), "公开列表不应含停用窗口")
        return f"{len(windows)} 个窗口"

    check("公开时间窗列表（V4 种子）", public_windows)

    # ================================================================ 2. 未排班时可用性全 false

    def availability_before_schedule() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/public/availability?serviceItemNo={item_no}"
                   f"&serviceAreaId={area_id}&dateFrom={iso(far)}&dateTo={iso(far)}")
        expect(status == 200, f"HTTP {status} code={payload.get('code')}")
        days = payload["data"]
        expect(len(days) == 1 and days[0]["serviceDate"] == iso(far), f"日期不对 {days}")
        # 服务时长 240 分钟放不进 180 分钟的晚上窗口，EVENING 必须被整窗过滤
        codes = [w["windowCode"] for w in days[0]["windows"]]
        expect("EVENING" not in codes, f"时长放不进的窗口不应出现，实际 {codes}")
        expect(all(w["available"] is False for w in days[0]["windows"]),
               f"未排班时不应可约：{days[0]}")
        return "2 窗口均不可约，晚上窗口被时长过滤"

    check("未排班 -> 可用性全 false 且时长不足的窗口被过滤", availability_before_schedule)

    # ================================================================ 3. 时间窗维护

    def create_overlap_window() -> str:
        return expect_error(api, "POST", "/api/v1/operator/service-windows",
                            409, "SERVICE_WINDOW_OVERLAP",
                            {"windowCode": "DUPLICATE", "windowName": "与上午重叠",
                             "startTime": "09:00:00", "endTime": "11:00:00"}, operator_token)

    check("新建与现有窗口重叠 -> 拒绝", create_overlap_window)

    def create_window() -> str:
        status, payload = api.call("POST", "/api/v1/operator/service-windows", {
            "windowCode": f"NOON{int(time.time()) % 100000}", "windowName": "午间",
            "startTime": "12:00:00", "endTime": "13:00:00", "sortOrder": 15}, operator_token)
        if status == 409:
            # 脏库降级：之前某轮已把 12:00-13:00 窗口建进 MORNING/AFTERNOON 的空隙
            # （首尾相接不重叠才建得进去），且停用窗口也参与重叠校验——改为复用它
            status, payload = api.call("GET", "/api/v1/operator/service-windows",
                                       token=operator_token)
            expect(status == 200, f"HTTP {status}")
            noon = [w for w in payload["data"]
                    if w["startTime"].startswith("12:00") and w["endTime"].startswith("13:00")]
            expect(noon, "12:00 重叠被拒但列表里找不到既有 12:00-13:00 窗口")
            state["noon_id"] = noon[0]["windowId"]
            state["noon_version"] = noon[0]["version"]
            return f"脏库降级：复用既有窗口 id={noon[0]['windowId']}"
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        state["noon_id"] = data["windowId"]
        state["noon_version"] = data["version"]
        # 12:00 结束与 12:00 开始不重叠（左闭右开），窗口才能建进 12:00-13:00 空隙
        return f"id={data['windowId']} v={data['version']}"

    check("新建首尾相接窗口 12:00-13:00 成功（左闭右开）", create_window)

    def duplicate_code_rejected() -> str:
        return expect_error(api, "POST", "/api/v1/operator/service-windows",
                            409, "SERVICE_WINDOW_OVERLAP",
                            {"windowCode": "NOON", "windowName": "重复编码",
                             "startTime": "21:30:00", "endTime": "23:00:00"}, operator_token)

    check("重复时间窗编码 -> 拒绝", duplicate_code_rejected)

    def update_version_conflict() -> str:
        return expect_error(api, "PUT", f"/api/v1/operator/service-windows/{state['noon_id']}",
                            409, "SERVICE_WINDOW_VERSION_CONFLICT",
                            {"startTime": "12:00:00", "endTime": "13:00:00",
                             "expectedVersion": 9999}, operator_token)

    check("过期版本修改窗口 -> 并发冲突", update_version_conflict)

    def update_window() -> str:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/service-windows/{state['noon_id']}",
            {"windowName": "午间休整", "startTime": "12:00:00", "endTime": "13:00:00",
             "status": "DISABLED", "expectedVersion": state["noon_version"]}, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["status"] == "DISABLED", f"应已停用，实际 {data['status']}")
        expect(data["version"] == state["noon_version"] + 1, "版本号应自增")
        return f"v={data['version']} DISABLED"

    check("正确版本修改并停用窗口", update_window)

    # ================================================================ 4. 运营批量排班

    def batch_schedule() -> str:
        status, payload = api.call("POST", "/api/v1/operator/schedules/batch", {
            "workerNo": state["worker_no"],
            "serviceDates": [iso(far), iso(far2)],
            "windowIds": [state["MORNING"], state["AFTERNOON"]],
            "scheduleType": "WORK", "note": "冒烟排班",
        }, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        schedules = payload["data"]
        expect(len(schedules) == 4, f"应生成 4 条排班，实际 {len(schedules)}")
        for s in schedules:
            expect(s["scheduleType"] == "WORK" and s["scheduleStatus"] == "ACTIVE", f"排班异常 {s}")
            expect(s["slotStatus"] == "AVAILABLE", f"WORK 排班应生成 AVAILABLE 时段 {s}")
        by_key = {(s["serviceDate"], s["windowCode"]): s["scheduleId"] for s in schedules}
        state["far_morning"] = by_key[(iso(far), "MORNING")]
        state["far_afternoon"] = by_key[(iso(far), "AFTERNOON")]
        state["far2_morning"] = by_key[(iso(far2), "MORNING")]
        state["far2_afternoon"] = by_key[(iso(far2), "AFTERNOON")]
        return "4 条排班，时段全部 AVAILABLE"

    check("运营批量排班 -> 生成 AVAILABLE 时段", batch_schedule)

    def duplicate_schedule_rejected() -> str:
        return expect_error(api, "POST", "/api/v1/operator/schedules/batch",
                            409, "WORKER_SCHEDULE_CONFLICT",
                            {"workerNo": state["worker_no"], "serviceDates": [iso(far)],
                             "windowIds": [state["MORNING"]], "scheduleType": "WORK"},
                            operator_token)

    check("同日同窗重复排班 -> 拒绝", duplicate_schedule_rejected)

    # ================================================================ 5. 排班后可用性

    def availability_after_schedule() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/public/availability?serviceItemNo={item_no}"
                   f"&serviceAreaId={area_id}&dateFrom={iso(far)}&dateTo={iso(far)}")
        expect(status == 200, f"HTTP {status}")
        for window in payload["data"][0]["windows"]:
            expect(window["available"] is True,
                   f"{iso(far)} {window['windowCode']} 库存/上限/资格应全通过：{window}")
        return "库存、接单上限、资格三级过滤全通过"

    check("排班后 -> 可用性为 true", availability_after_schedule)

    # ================================================================ 6. 人员自助报班

    def self_schedule_evening() -> str:
        status, payload = api.call("POST", "/api/v1/worker/schedules", {
            "serviceDates": [iso(far2)], "windowIds": [state["EVENING"]],
            "scheduleType": "WORK", "note": "自愿加班",
        }, state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(len(data) == 1 and data[0]["slotStatus"] == "AVAILABLE", f"实际 {data}")
        return f"{data[0]['serviceDate']} EVENING AVAILABLE"

    check("自助报班（远期晚上）成功", self_schedule_evening)

    def self_schedule_today_rejected() -> str:
        return expect_error(api, "POST", "/api/v1/worker/schedules",
                            409, "WORKER_SCHEDULE_CONFLICT",
                            {"serviceDates": [iso(date.today())],
                             "windowIds": [state["AFTERNOON"]], "scheduleType": "WORK"},
                            state["worker_token"])

    check("自助报班当天 -> 拒绝（必须提前一天）", self_schedule_today_rejected)

    def self_schedule_leave_rejected() -> str:
        return expect_error(api, "POST", "/api/v1/worker/schedules",
                            400, "VALIDATION_FAILED",
                            {"serviceDates": [iso(far3)], "windowIds": [state["MORNING"]],
                             "scheduleType": "LEAVE"},
                            state["worker_token"])

    check("自助报班请假类型 -> 拒绝（请假走独立入口）", self_schedule_leave_rejected)

    def my_schedules() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/worker/schedules?dateFrom={iso(far)}&dateTo={iso(far3)}",
            token=state["worker_token"])
        expect(status == 200, f"HTTP {status}")
        schedules = payload["data"]
        expect(len(schedules) == 5, f"应有 5 条排班（批量 4 + 自助报班 1，请假在其后），"
                                    f"实际 {len(schedules)}")
        expect(all(s["workerNo"] == state["worker_no"] for s in schedules), "应只含本人排班")
        return f"{len(schedules)} 条"

    check("本人排班列表仅含自己的排班", my_schedules)

    # ================================================================ 7. 请假

    def self_leave_conflict() -> str:
        return expect_error(api, "POST", "/api/v1/worker/leaves",
                            409, "WORKER_SCHEDULE_CONFLICT",
                            {"serviceDate": iso(far2), "windowId": state["MORNING"],
                             "note": "已有工作排班"}, state["worker_token"])

    check("已有排班的日期窗口请假 -> 唯一约束拒绝", self_leave_conflict)

    def self_leave() -> str:
        status, payload = api.call("POST", "/api/v1/worker/leaves", {
            "serviceDate": iso(far3), "windowId": state["MORNING"], "note": "身体不适",
        }, state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["scheduleType"] == "LEAVE", f"应为 LEAVE，实际 {data['scheduleType']}")
        expect(data["slotStatus"] == "BLOCKED", f"请假时段应为 BLOCKED，实际 {data['slotStatus']}")
        return f"{data['serviceDate']} MORNING BLOCKED"

    check("自助请假（远期上午）-> LEAVE + BLOCKED", self_leave)

    def operator_leave() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/schedules/{state['far_morning']}/leave",
            None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["scheduleType"] == "LEAVE", f"应为 LEAVE，实际 {data['scheduleType']}")
        expect(data["slotStatus"] == "BLOCKED", f"时段应为 BLOCKED，实际 {data['slotStatus']}")
        return f"排班 {state['far_morning']} -> LEAVE/BLOCKED"

    check("运营替人员请假", operator_leave)

    # ================================================================ 8. 修改排班

    def update_schedule() -> str:
        status, payload = api.call(
            "PUT", f"/api/v1/operator/schedules/{state['far_afternoon']}",
            {"serviceDate": iso(far), "windowId": state["EVENING"], "note": "调到晚上"},
            operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["windowCode"] == "EVENING", f"应已改到晚上，实际 {data['windowCode']}")
        expect(data["slotStatus"] == "AVAILABLE", f"移动后时段仍应空闲，实际 {data['slotStatus']}")
        return "远期下午 -> 远期晚上"

    check("修改排班（换时间窗）成功", update_schedule)

    # ================================================================ 9. 停排

    def self_cancel() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/worker/schedules/{state['far2_afternoon']}/cancel",
            None, state["worker_token"])
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["scheduleStatus"] == "CANCELED", f"排班应取消，实际 {data['scheduleStatus']}")
        expect(data["slotStatus"] == "BLOCKED", f"空闲时段应转 BLOCKED，实际 {data['slotStatus']}")
        return f"排班 {state['far2_afternoon']} CANCELED/BLOCKED"

    check("自助取消排班 -> CANCELED + BLOCKED", self_cancel)

    def cancel_twice_rejected() -> str:
        return expect_error(api, "POST",
                            f"/api/v1/worker/schedules/{state['far2_afternoon']}/cancel",
                            409, "WORKER_SCHEDULE_CONFLICT", None, state["worker_token"])

    check("重复取消同一排班 -> 拒绝", cancel_twice_rejected)

    def operator_disable() -> str:
        status, payload = api.call(
            "POST", f"/api/v1/operator/schedules/{state['far2_morning']}/disable",
            None, operator_token)
        expect(status == 200, f"HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
        data = payload["data"]
        expect(data["scheduleStatus"] == "CANCELED", f"实际 {data['scheduleStatus']}")
        expect(data["slotStatus"] == "BLOCKED", f"实际 {data['slotStatus']}")
        return f"排班 {state['far2_morning']} 停排"

    check("运营停排", operator_disable)

    # ================================================================ 10. 权限隔离

    def customer_forbidden() -> str:
        phone = new_phone("136")
        status, payload = api.call("POST", "/api/v1/auth/customer/register", {
            "phone": phone, "password": "Hestia2026", "displayName": "冒烟客户"})
        expect(status == 200, f"注册失败 HTTP {status} code={payload.get('code')}")
        customer_token = payload["data"]["accessToken"]
        status, payload = api.call("GET", f"/api/v1/operator/schedules?dateFrom={iso(far)}"
                                          f"&dateTo={iso(far2)}", token=customer_token)
        expect(status == 403, f"消费者访问运营排班接口应为 403，实际 {status}")
        status, payload = api.call("GET", f"/api/v1/worker/schedules?dateFrom={iso(far)}"
                                          f"&dateTo={iso(far2)}", token=customer_token)
        expect(status == 403, f"消费者访问人员排班接口应为 403，实际 {status}")
        status, payload = api.call("POST", "/api/v1/operator/service-windows", {
            "windowCode": "HACK", "windowName": "越权", "startTime": "22:00:00",
            "endTime": "23:00:00"}, customer_token)
        expect(status == 403, f"消费者建时间窗应为 403，实际 {status}")
        return "operator/worker 接口均 403"

    check("消费者访问排班管理接口 -> 403", customer_forbidden)

    def availability_public_no_token() -> str:
        status, payload = api.call(
            "GET", f"/api/v1/public/availability?serviceItemNo={item_no}"
                   f"&serviceAreaId={area_id}&dateFrom={iso(far)}&dateTo={iso(far)}")
        expect(status == 200, f"公开接口不应要求登录，实际 {status}")
        return "200 无需登录"

    check("可用性查询为公开接口", availability_public_no_token)

    # ================================================================ 汇总
    print()
    print("=" * 78)
    print(f"通过 {len(PASSED)} 项，失败 {len(FAILED)} 项。")
    if FAILED:
        print("失败项：")
        for name in FAILED:
            print(f"  - {name}")
        return 1
    print("批次 2（排班与可用性）冒烟验证全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
