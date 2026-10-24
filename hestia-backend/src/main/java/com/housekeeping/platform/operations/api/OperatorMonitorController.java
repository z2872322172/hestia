package com.housekeeping.platform.operations.api;

import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.dispatch.application.contract.DispatchOperationsStatsService;
import com.housekeeping.platform.fulfillment.application.contract.FulfillmentOperationsStatsService;
import com.housekeeping.platform.identity.application.contract.AreaScope;
import com.housekeeping.platform.identity.application.contract.OperatorAreaScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 运营监控接口（docs/modules/13 第 17.1 节）。
 *
 * <p>订单 / 派单 / 履约三个维度的实时监控；数据范围与工作台一致。</p>
 */
@RestController
@RequestMapping("/api/v1/operator")
@RequiresPersistence
@Tag(name = "运营-监控", description = "订单、派单、履约实时监控")
public class OperatorMonitorController {

    private final BookingOperationsStatsService bookingStats;

    private final DispatchOperationsStatsService dispatchStats;

    private final FulfillmentOperationsStatsService fulfillmentStats;

    private final OperatorAreaScopeService areaScopeService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorMonitorController(BookingOperationsStatsService bookingStats,
                                     DispatchOperationsStatsService dispatchStats,
                                     FulfillmentOperationsStatsService fulfillmentStats,
                                     OperatorAreaScopeService areaScopeService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.bookingStats = bookingStats;
        this.dispatchStats = dispatchStats;
        this.fulfillmentStats = fulfillmentStats;
        this.areaScopeService = areaScopeService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 派单监控视图。
     *
     * @param pendingAccept  待接单
     * @param manualRequired 待人工派单
     * @param autoSuccess    当日自动派单接单成功数
     * @param rejected       当日拒单数
     * @param timeout        当日接单超时数
     * @param manual         当日人工派单数
     */
    public record DispatchMonitorView(long pendingAccept, long manualRequired, long autoSuccess,
                                      long rejected, long timeout, long manual) {
    }

    /**
     * 履约监控视图。
     *
     * @param pending        待出发
     * @param enRoute        已出发
     * @param checkedIn      已签到
     * @param inService      服务中
     * @param pendingConfirm 待客户确认
     * @param exceptionCount 异常
     */
    public record FulfillmentMonitorView(long pending, long enRoute, long checkedIn,
                                         long inService, long pendingConfirm,
                                         long exceptionCount) {
    }

    /**
     * 订单监控分页。
     *
     * @param status          主状态过滤（可空）
     * @param appointmentDate 服务日期过滤（可空）
     * @param page            页码
     * @param size            条数
     * @return 分页订单摘要
     */
    @GetMapping("/orders/monitor")
    @Operation(summary = "订单监控", description = "按状态 / 服务日期分页查询授权区域订单")
    public Result<PageResult<BookingSummaryView>> orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appointmentDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(bookingStats.monitorOrders(requireAreaIds(), status, appointmentDate,
                page, size));
    }

    /**
     * 派单监控。
     *
     * @param date 行为统计日期，默认今天
     * @return 队列与当日派单行为计数
     */
    @GetMapping("/dispatch/monitor")
    @Operation(summary = "派单监控", description = "待接单 / 待人工派单队列与当日派单行为计数")
    public Result<DispatchMonitorView> dispatch(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Collection<Long> areaIds = requireAreaIds();
        LocalDate statDate = date == null ? LocalDate.now() : date;
        DispatchOperationsStatsService.DispatchQueueStat queue = dispatchStats.queueStat(areaIds);
        long autoSuccess = sum(dispatchStats.autoSuccessDaily(statDate, statDate));
        long rejected = sum(dispatchStats.rejectedDaily(statDate, statDate));
        long timeout = sum(dispatchStats.timeoutDaily(statDate, statDate));
        long manual = sum(dispatchStats.manualDaily(statDate, statDate));
        return Result.ok(new DispatchMonitorView(queue.pendingAccept(), queue.manualRequired(),
                autoSuccess, rejected, timeout, manual));
    }

    /**
     * 履约监控。
     *
     * @return 各进行中状态的记录数
     */
    @GetMapping("/fulfillment/monitor")
    @Operation(summary = "履约监控", description = "待出发 / 已出发 / 已签到 / 服务中 / 待确认 / 异常计数")
    public Result<FulfillmentMonitorView> fulfillment() {
        Collection<Long> areaIds = requireAreaIds();
        return Result.ok(new FulfillmentMonitorView(
                fulfillmentStats.countByStatus(areaIds, "PENDING"),
                fulfillmentStats.countByStatus(areaIds, "EN_ROUTE"),
                fulfillmentStats.countByStatus(areaIds, "CHECKED_IN"),
                fulfillmentStats.countByStatus(areaIds, "IN_SERVICE"),
                fulfillmentStats.countByStatus(areaIds, "PENDING_CONFIRM"),
                fulfillmentStats.countByStatus(areaIds, "EXCEPTION")));
    }

    private Collection<Long> requireAreaIds() {
        long accountId = currentAccountResolver.requireAccountId();
        AreaScope scope = areaScopeService.resolveAreaScope(accountId,
                currentAccountResolver.currentRoleCode());
        if (!scope.allAreas() && scope.areaIds().isEmpty()) {
            throw new BusinessException(ErrorCode.DASHBOARD_DATA_FORBIDDEN);
        }
        return scope.areaIds();
    }

    private static long sum(List<com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow> rows) {
        return rows.stream().mapToLong(row -> row.getCnt() == null ? 0L : row.getCnt()).sum();
    }
}
