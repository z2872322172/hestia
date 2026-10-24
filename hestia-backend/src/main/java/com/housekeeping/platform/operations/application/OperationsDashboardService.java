package com.housekeeping.platform.operations.application;

import com.housekeeping.platform.aftersales.application.contract.AftersalesOperationsStatsService;
import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.dispatch.application.contract.DispatchOperationsStatsService;
import com.housekeeping.platform.fulfillment.application.contract.FulfillmentOperationsStatsService;
import com.housekeeping.platform.identity.application.contract.AreaScope;
import com.housekeeping.platform.identity.application.contract.OperatorAreaScopeService;
import com.housekeeping.platform.payment.application.contract.PaymentOperationsStatsService;
import com.housekeeping.platform.workforce.application.contract.WorkforceOperationsStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 运营工作台应用服务（docs/modules/13 第 5 节）。
 *
 * <p>数据范围：OPERATOR 按授权区域、ADMIN 全量、未配置区域直接拒绝
 * （DASHBOARD_DATA_FORBIDDEN）。所有业务计数来自各模块的公开统计契约，
 * 本服务不触碰其他模块的表。</p>
 */
@Service
@RequiresPersistence
public class OperationsDashboardService {

    private static final String FULFILLMENT_IN_SERVICE = "IN_SERVICE";

    private static final String FULFILLMENT_EXCEPTION = "EXCEPTION";

    private static final String DEFAULT_CERT_NOTICE_DAYS_KEY = "certificate_expiry_notice_days";

    private static final long DEFAULT_CERT_NOTICE_DAYS = 30L;

    private final OperatorAreaScopeService areaScopeService;

    private final CurrentAccountResolver currentAccountResolver;

    private final BookingOperationsStatsService bookingStats;

    private final DispatchOperationsStatsService dispatchStats;

    private final FulfillmentOperationsStatsService fulfillmentStats;

    private final PaymentOperationsStatsService paymentStats;

    private final AftersalesOperationsStatsService aftersalesStats;

    private final WorkforceOperationsStatsService workforceStats;

    private final SystemConfigService systemConfigService;

    public OperationsDashboardService(OperatorAreaScopeService areaScopeService,
                                      CurrentAccountResolver currentAccountResolver,
                                      BookingOperationsStatsService bookingStats,
                                      DispatchOperationsStatsService dispatchStats,
                                      FulfillmentOperationsStatsService fulfillmentStats,
                                      PaymentOperationsStatsService paymentStats,
                                      AftersalesOperationsStatsService aftersalesStats,
                                      WorkforceOperationsStatsService workforceStats,
                                      SystemConfigService systemConfigService) {
        this.areaScopeService = areaScopeService;
        this.currentAccountResolver = currentAccountResolver;
        this.bookingStats = bookingStats;
        this.dispatchStats = dispatchStats;
        this.fulfillmentStats = fulfillmentStats;
        this.paymentStats = paymentStats;
        this.aftersalesStats = aftersalesStats;
        this.workforceStats = workforceStats;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 工作台汇总视图。
     *
     * @param created              今日下单量
     * @param pendingPayment       今日待支付
     * @param completed            今日已完成
     * @param canceled             今日已取消
     * @param pendingAccept        今日待接单
     * @param manualRequired       待人工派单
     * @param inService            服务中
     * @param exceptionCount       异常订单
     * @param pendingRefund        待处理退款
     * @param openTickets          待处理投诉
     * @param expiringCertificates 证书即将过期人员
     */
    public record DashboardSummaryView(long created, long pendingPayment, long completed,
                                       long canceled, long pendingAccept, long manualRequired,
                                       long inService, long exceptionCount, long pendingRefund,
                                       long openTickets, long expiringCertificates) {
    }

    /**
     * 告警项。
     *
     * @param type  告警类型
     * @param count 数量
     * @param level HIGH / MEDIUM
     */
    public record AlertView(String type, long count, String level) {
    }

    /**
     * 工作台汇总（docs/modules/13 第 5 节首页指标）。
     *
     * @param date 统计日期（通常为今天）
     * @return 汇总视图
     */
    @Transactional(readOnly = true)
    public DashboardSummaryView summary(LocalDate date) {
        Collection<Long> areaIds = requireScope().areaIds();
        BookingOperationsStatsService.BookingTodayStat today = bookingStats.todayStat(areaIds, date);
        DispatchOperationsStatsService.DispatchQueueStat queue = dispatchStats.queueStat(areaIds);
        long inService = fulfillmentStats.countByStatus(areaIds, FULFILLMENT_IN_SERVICE);
        long exceptionCount = fulfillmentStats.countByStatus(areaIds, FULFILLMENT_EXCEPTION);
        long pendingRefund = paymentStats.pendingRefundCount(areaIds);
        long openTickets = aftersalesStats.openTicketCount(areaIds);
        long noticeDays = systemConfigService.integerValue(
                DEFAULT_CERT_NOTICE_DAYS_KEY, DEFAULT_CERT_NOTICE_DAYS);
        long expiring = workforceStats.expiringWorkerCount(areaIds, date, (int) noticeDays);
        return new DashboardSummaryView(today.created(), today.pendingPayment(),
                today.completed(), today.canceled(), queue.pendingAccept(), queue.manualRequired(),
                inService, exceptionCount, pendingRefund, openTickets, expiring);
    }

    /**
     * 工作台告警（只返回数量大于 0 的项）。
     *
     * @return 告警列表
     */
    @Transactional(readOnly = true)
    public List<AlertView> alerts() {
        AreaScope scope = requireScope();
        Collection<Long> areaIds = scope.areaIds();
        LocalDate today = LocalDate.now();
        DispatchOperationsStatsService.DispatchQueueStat queue = dispatchStats.queueStat(areaIds);
        long pendingRefund = paymentStats.pendingRefundCount(areaIds);
        long openTickets = aftersalesStats.openTicketCount(areaIds);
        long exceptionCount = fulfillmentStats.countByStatus(areaIds, FULFILLMENT_EXCEPTION);
        long noticeDays = systemConfigService.integerValue(
                DEFAULT_CERT_NOTICE_DAYS_KEY, DEFAULT_CERT_NOTICE_DAYS);
        long expiring = workforceStats.expiringWorkerCount(areaIds, today, (int) noticeDays);

        List<AlertView> alerts = new ArrayList<>();
        if (queue.manualRequired() > 0) {
            alerts.add(new AlertView("MANUAL_ASSIGNMENT_PENDING", queue.manualRequired(), "HIGH"));
        }
        if (pendingRefund > 0) {
            alerts.add(new AlertView("REFUND_PENDING", pendingRefund, "HIGH"));
        }
        if (exceptionCount > 0) {
            alerts.add(new AlertView("ORDER_EXCEPTION", exceptionCount, "HIGH"));
        }
        if (openTickets > 0) {
            alerts.add(new AlertView("TICKET_OPEN", openTickets, "MEDIUM"));
        }
        if (expiring > 0) {
            alerts.add(new AlertView("CERTIFICATE_EXPIRING", expiring, "MEDIUM"));
        }
        return alerts;
    }

    private AreaScope requireScope() {
        long accountId = currentAccountResolver.requireAccountId();
        AreaScope scope = areaScopeService.resolveAreaScope(accountId,
                currentAccountResolver.currentRoleCode());
        if (!scope.allAreas() && scope.areaIds().isEmpty()) {
            throw new BusinessException(ErrorCode.DASHBOARD_DATA_FORBIDDEN);
        }
        return scope;
    }
}
