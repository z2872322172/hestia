package com.housekeeping.platform.scheduling.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.scheduling.api.dto.BatchScheduleRequest;
import com.housekeeping.platform.scheduling.api.dto.LeaveCreateRequest;
import com.housekeeping.platform.scheduling.application.WorkerScheduleService;
import com.housekeeping.platform.scheduling.application.view.ScheduleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 服务人员排班自助接口。
 *
 * <p>所有接口都不接受 {@code workerNo} 参数：排班归属由安全上下文解析，
 * 人员无法通过改参数查看或修改他人排班。自助排班只允许 {@code WORK} 类型
 * 且至少提前一天；请假走独立入口，避免"自助请假"与"自助报班"
 * 在同一请求里混出既工作又请假的矛盾状态。</p>
 */
@RestController
@RequestMapping("/api/v1/worker")
@Validated
@RequiresPersistence
@Tag(name = "服务人员-排班", description = "本人排班、请假与停排")
public class WorkerScheduleController {

    private final WorkerScheduleService scheduleService;

    private final CurrentAccountResolver currentAccountResolver;

    public WorkerScheduleController(WorkerScheduleService scheduleService,
                                    CurrentAccountResolver currentAccountResolver) {
        this.scheduleService = scheduleService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 我的排班。
     *
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 排班视图列表
     */
    @GetMapping("/schedules")
    @Operation(summary = "我的排班列表")
    public Result<List<ScheduleView>> schedules(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(scheduleService.listMy(currentAccountResolver.requireAccountId(),
                dateFrom, dateTo));
    }

    /**
     * 自助报班（只允许 WORK，日期必须明天及以后）。
     *
     * @param request 报班请求
     * @return 生成的排班视图
     */
    @PostMapping("/schedules")
    @Operation(summary = "自助报班")
    public Result<List<ScheduleView>> batchCreate(@Valid @RequestBody BatchScheduleRequest request) {
        return Result.ok(scheduleService.batchCreate(request.toCommand(),
                currentAccountResolver.requireAccountId(), true));
    }

    /**
     * 取消我的排班（停排）：空闲时段转 BLOCKED，已有订单占用则拒绝。
     *
     * @param scheduleId 排班 ID
     * @return 更新后的排班视图
     */
    @PostMapping("/schedules/{scheduleId}/cancel")
    @Operation(summary = "取消我的排班")
    public Result<ScheduleView> cancel(@PathVariable long scheduleId) {
        return Result.ok(scheduleService.cancel(scheduleId,
                currentAccountResolver.requireAccountId(), true));
    }

    /**
     * 提交请假：生成一条 LEAVE 排班（时段为 BLOCKED），至少提前一天。
     *
     * @param request 请假请求
     * @return 请假排班视图
     */
    @PostMapping("/leaves")
    @Operation(summary = "提交请假")
    public Result<ScheduleView> submitLeave(@Valid @RequestBody LeaveCreateRequest request) {
        return Result.ok(scheduleService.submitMyLeave(currentAccountResolver.requireAccountId(),
                request.serviceDate(), request.windowId(), request.note()));
    }
}
