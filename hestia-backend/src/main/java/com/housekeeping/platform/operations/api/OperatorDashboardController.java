package com.housekeeping.platform.operations.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.operations.application.OperationsDashboardService;
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
 * 运营工作台接口（docs/modules/13 第 17.1 节）。
 *
 * <p>首页汇总与告警；OPERATOR 只能看到授权区域的数据，
 * 未配置区域返回 DASHBOARD_DATA_FORBIDDEN。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/dashboard")
@RequiresPersistence
@Tag(name = "运营-工作台", description = "运营首页汇总与告警")
public class OperatorDashboardController {

    private final OperationsDashboardService dashboardService;

    public OperatorDashboardController(OperationsDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 工作台汇总。
     *
     * @param date 统计日期，默认今天
     * @return 汇总视图
     */
    @GetMapping("/summary")
    @Operation(summary = "工作台汇总", description = "今日下单 / 待支付 / 待接单 / 服务中 / 待退款等计数")
    public Result<OperationsDashboardService.DashboardSummaryView> summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dashboardService.summary(date == null ? LocalDate.now() : date));
    }

    /**
     * 工作台告警。
     *
     * @return 告警列表（只含数量大于 0 的项）
     */
    @GetMapping("/alerts")
    @Operation(summary = "工作台告警", description = "待人工派单 / 待退款 / 异常订单 / 投诉 / 证书到期提醒")
    public Result<List<OperationsDashboardService.AlertView>> alerts() {
        return Result.ok(dashboardService.alerts());
    }
}
