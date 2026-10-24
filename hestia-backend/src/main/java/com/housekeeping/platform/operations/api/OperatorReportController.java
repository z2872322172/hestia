package com.housekeeping.platform.operations.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.operations.application.OperationsReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 运营报表接口（docs/modules/13 第 17.2 节）。
 *
 * <p>订单 / 支付 / 人员 / 售后实时报表与日报读模型查询；
 * 日期区间必填且不超过 92 天（REPORT_RANGE_INVALID）。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/reports")
@RequiresPersistence
@Tag(name = "运营-报表", description = "订单、支付、人员时段、售后报表与日报")
public class OperatorReportController {

    private final OperationsReportService reportService;

    public OperatorReportController(OperationsReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 订单报表。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日区域行
     */
    @GetMapping("/orders")
    @Operation(summary = "订单报表", description = "按创建日统计的创建 / 完成 / 取消数")
    public Result<List<OperationsReportService.OrderReportRow>> orders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.ordersReport(from, to));
    }

    /**
     * 支付报表。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日区域行
     */
    @GetMapping("/payments")
    @Operation(summary = "支付报表", description = "支付金额与退款金额（按各自完成日归属）")
    public Result<List<OperationsReportService.PaymentReportRow>> payments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.paymentsReport(from, to));
    }

    /**
     * 人员时段报表。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日全公司行
     */
    @GetMapping("/workers")
    @Operation(summary = "人员时段报表", description = "时段总量、占用数与利用率（全公司口径）")
    public Result<List<OperationsReportService.WorkerReportRow>> workers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.workersReport(from, to));
    }

    /**
     * 售后报表。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日区域行
     */
    @GetMapping("/after-sales")
    @Operation(summary = "售后报表", description = "投诉与返工工单数（按提交日归属）")
    public Result<List<OperationsReportService.AfterSalesReportRow>> afterSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.afterSalesReport(from, to));
    }

    /**
     * 日报读模型。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @param page 页码
     * @param size 条数
     * @return 分页指标行
     */
    @GetMapping("/daily")
    @Operation(summary = "日报读模型", description = "operation_daily_metric 区间分页查询")
    public Result<PageResult<OperationsReportService.DailyMetricView>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(reportService.dailyReport(from, to, page, size));
    }
}
