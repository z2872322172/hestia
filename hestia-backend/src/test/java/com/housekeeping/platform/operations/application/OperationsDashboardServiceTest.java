package com.housekeeping.platform.operations.application;

import com.housekeeping.platform.aftersales.application.contract.AftersalesOperationsStatsService;
import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.dispatch.application.contract.DispatchOperationsStatsService;
import com.housekeeping.platform.fulfillment.application.contract.FulfillmentOperationsStatsService;
import com.housekeeping.platform.identity.application.contract.AreaScope;
import com.housekeeping.platform.identity.application.contract.OperatorAreaScopeService;
import com.housekeeping.platform.payment.application.contract.PaymentOperationsStatsService;
import com.housekeeping.platform.workforce.application.contract.WorkforceOperationsStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 运营工作台服务测试（docs/modules/13 第 20.2 节"运营人员只能查看授权区域"）。
 */
@ExtendWith(MockitoExtension.class)
class OperationsDashboardServiceTest {

    private static final long ACCOUNT_ID = 7L;

    @Mock
    private OperatorAreaScopeService areaScopeService;

    @Mock
    private CurrentAccountResolver currentAccountResolver;

    @Mock
    private BookingOperationsStatsService bookingStats;

    @Mock
    private DispatchOperationsStatsService dispatchStats;

    @Mock
    private FulfillmentOperationsStatsService fulfillmentStats;

    @Mock
    private PaymentOperationsStatsService paymentStats;

    @Mock
    private AftersalesOperationsStatsService aftersalesStats;

    @Mock
    private WorkforceOperationsStatsService workforceStats;

    @Mock
    private SystemConfigService systemConfigService;

    private OperationsDashboardService service;

    @BeforeEach
    void setUp() {
        service = new OperationsDashboardService(areaScopeService, currentAccountResolver,
                bookingStats, dispatchStats, fulfillmentStats, paymentStats,
                aftersalesStats, workforceStats, systemConfigService);
    }

    @Test
    @DisplayName("汇总：各模块契约计数正确拼装")
    void assemblesSummaryFromContracts() {
        stubScope(AreaScope.ofAreas(List.of(1L, 2L)));
        when(bookingStats.todayStat(any(), any()))
                .thenReturn(new BookingOperationsStatsService.BookingTodayStat(10, 4, 3, 1));
        when(dispatchStats.queueStat(any()))
                .thenReturn(new DispatchOperationsStatsService.DispatchQueueStat(2, 1));
        when(fulfillmentStats.countByStatus(any(), eq("IN_SERVICE"))).thenReturn(5L);
        when(fulfillmentStats.countByStatus(any(), eq("EXCEPTION"))).thenReturn(2L);
        when(paymentStats.pendingRefundCount(any())).thenReturn(3L);
        when(aftersalesStats.openTicketCount(any())).thenReturn(4L);
        when(systemConfigService.integerValue(eq("certificate_expiry_notice_days"), eq(30L)))
                .thenReturn(30L);
        when(workforceStats.expiringWorkerCount(any(), any(), eq(30))).thenReturn(6L);

        OperationsDashboardService.DashboardSummaryView summary = service.summary(LocalDate.now());

        assertThat(summary.created()).isEqualTo(10);
        assertThat(summary.pendingPayment()).isEqualTo(4);
        assertThat(summary.completed()).isEqualTo(3);
        assertThat(summary.canceled()).isEqualTo(1);
        assertThat(summary.pendingAccept()).isEqualTo(2);
        assertThat(summary.manualRequired()).isEqualTo(1);
        assertThat(summary.inService()).isEqualTo(5);
        assertThat(summary.exceptionCount()).isEqualTo(2);
        assertThat(summary.pendingRefund()).isEqualTo(3);
        assertThat(summary.openTickets()).isEqualTo(4);
        assertThat(summary.expiringCertificates()).isEqualTo(6);
    }

    @Test
    @DisplayName("未配置区域的运营：看板与告警一律拒绝")
    void rejectsOperatorWithoutAreas() {
        stubScope(AreaScope.ofAreas(List.of()));

        assertThatThrownBy(() -> service.summary(LocalDate.now()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DASHBOARD_DATA_FORBIDDEN));
        assertThatThrownBy(() -> service.alerts())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DASHBOARD_DATA_FORBIDDEN));
    }

    @Test
    @DisplayName("告警：只返回数量大于 0 的项，级别正确")
    void returnsOnlyPositiveAlerts() {
        stubScope(AreaScope.all());
        when(dispatchStats.queueStat(anyCollection()))
                .thenReturn(new DispatchOperationsStatsService.DispatchQueueStat(0, 1));
        when(paymentStats.pendingRefundCount(anyCollection())).thenReturn(3L);
        when(fulfillmentStats.countByStatus(anyCollection(), eq("EXCEPTION"))).thenReturn(0L);
        when(aftersalesStats.openTicketCount(anyCollection())).thenReturn(4L);
        when(systemConfigService.integerValue(eq("certificate_expiry_notice_days"), eq(30L)))
                .thenReturn(30L);
        when(workforceStats.expiringWorkerCount(anyCollection(), any(), eq(30))).thenReturn(6L);

        List<OperationsDashboardService.AlertView> alerts = service.alerts();

        assertThat(alerts).extracting(OperationsDashboardService.AlertView::type)
                .containsExactly("MANUAL_ASSIGNMENT_PENDING", "REFUND_PENDING",
                        "TICKET_OPEN", "CERTIFICATE_EXPIRING");
        assertThat(alerts).extracting(OperationsDashboardService.AlertView::level)
                .containsExactly("HIGH", "HIGH", "MEDIUM", "MEDIUM");
    }

    private void stubScope(AreaScope scope) {
        when(currentAccountResolver.requireAccountId()).thenReturn(ACCOUNT_ID);
        when(currentAccountResolver.currentRoleCode()).thenReturn("OPERATOR");
        when(areaScopeService.resolveAreaScope(ACCOUNT_ID, "OPERATOR")).thenReturn(scope);
    }
}
