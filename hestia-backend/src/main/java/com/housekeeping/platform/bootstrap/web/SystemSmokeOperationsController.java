package com.housekeeping.platform.bootstrap.web;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.operations.application.DailyMetricJob;
import com.housekeeping.platform.operations.application.ExportApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运营模块冒烟接口：驱动日报重算与导出任务处理。
 *
 * <p>生产环境随 smoke 接口一并关闭（app.feature.persistence-enabled）。</p>
 */
@RestController
@RequestMapping("/api/v1/system/smoke/operations")
@ConditionalOnExpression("${app.feature.persistence-enabled:true}")
@Tag(name = "系统冒烟-运营", description = "日报重算与导出任务处理")
public class SystemSmokeOperationsController {

    private final DailyMetricJob dailyMetricJob;

    private final ExportApplicationService exportService;

    public SystemSmokeOperationsController(DailyMetricJob dailyMetricJob,
                                           ExportApplicationService exportService) {
        this.dailyMetricJob = dailyMetricJob;
        this.exportService = exportService;
    }

    /**
     * 重算指定日期的日报指标（幂等，可重复调用验证一致性）。
     *
     * @param date 统计日期，默认今天
     * @return UPSERT 的指标行数
     */
    @PostMapping("/daily-metric")
    @Operation(summary = "重算日报", description = "按 (metric_date, service_area_id) UPSERT，重复执行结果一致")
    public Result<Integer> recalculateDailyMetric(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dailyMetricJob.runOnce(date == null ? LocalDate.now() : date));
    }

    /**
     * 处理一批待导出任务。
     *
     * @param batchSize 单批数量，默认 5
     * @return 本次成功生成的文件数
     */
    @PostMapping("/exports/run-once")
    @Operation(summary = "处理导出任务", description = "认领 PENDING 任务并生成 CSV 上传私有桶")
    public Result<Integer> runExports(
            @RequestParam(defaultValue = "5") int batchSize) {
        return Result.ok(exportService.runOnce(LocalDateTime.now(), batchSize));
    }
}
