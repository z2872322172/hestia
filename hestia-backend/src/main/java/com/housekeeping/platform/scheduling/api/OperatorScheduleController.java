package com.housekeeping.platform.scheduling.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.scheduling.api.dto.BatchScheduleRequest;
import com.housekeeping.platform.scheduling.api.dto.ScheduleUpdateRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班管理接口（运营端）。
 *
 * <p>运营可以给任何人排班（含当天），也能替人员请假或停排。
 * 所有变更动作遇到已锁定/已占用时段一律拒绝——订单占用只能走改派或改期，
 * 这是排班变更与订单协调的唯一边界。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/schedules")
@Validated
@RequiresPersistence
@Tag(name = "运营-排班管理", description = "批量排班、修改、请假、停排与查询")
public class OperatorScheduleController {

    private final WorkerScheduleService scheduleService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorScheduleController(WorkerScheduleService scheduleService,
                                      CurrentAccountResolver currentAccountResolver) {
        this.scheduleService = scheduleService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 排班查询：按日期区间查全量，或指定人员编号。
     *
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @param workerNo 人员编号，可空表示全部人员
     * @return 排班视图列表
     */
    @GetMapping
    @Operation(summary = "排班列表")
    public Result<List<ScheduleView>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String workerNo) {
        if (workerNo == null || workerNo.isBlank()) {
            return Result.ok(scheduleService.listByDateRange(dateFrom, dateTo));
        }
        return Result.ok(scheduleService.listByWorkerNo(workerNo, dateFrom, dateTo));
    }

    /**
     * 批量创建排班并生成时段（整批一个事务）。
     *
     * @param request 批量排班请求
     * @return 生成的排班视图
     */
    @PostMapping("/batch")
    @Operation(summary = "批量创建排班")
    public Result<List<ScheduleView>> batchCreate(@Valid @RequestBody BatchScheduleRequest request) {
        return Result.ok(scheduleService.batchCreate(request.toCommand(),
                currentAccountResolver.requireAccountId(), false));
    }

    /**
     * 修改排班（改日期/时间窗），仅空闲时段允许。
     *
     * @param scheduleId 排班 ID
     * @param request    修改请求
     * @return 更新后的排班视图
     */
    @PutMapping("/{scheduleId}")
    @Operation(summary = "修改排班")
    public Result<ScheduleView> update(@PathVariable long scheduleId,
                                       @Valid @RequestBody ScheduleUpdateRequest request) {
        return Result.ok(scheduleService.update(scheduleId, request.serviceDate(),
                request.windowId(), request.note(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 替人员请假：排班转 LEAVE，空闲时段转 BLOCKED。
     *
     * @param scheduleId 排班 ID
     * @return 更新后的排班视图
     */
    @PostMapping("/{scheduleId}/leave")
    @Operation(summary = "替人员请假")
    public Result<ScheduleView> leave(@PathVariable long scheduleId) {
        return Result.ok(scheduleService.markLeave(scheduleId,
                currentAccountResolver.requireAccountId(), false));
    }

    /**
     * 停排：排班置 CANCELED，空闲时段按日期转 BLOCKED 或 CLOSED。
     *
     * @param scheduleId 排班 ID
     * @return 更新后的排班视图
     */
    @PostMapping("/{scheduleId}/disable")
    @Operation(summary = "停排")
    public Result<ScheduleView> disable(@PathVariable long scheduleId) {
        return Result.ok(scheduleService.cancel(scheduleId,
                currentAccountResolver.requireAccountId(), false));
    }
}
