package com.housekeeping.platform.operations.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.aftersales.application.contract.AftersalesOperationsStatsService;
import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.identity.application.contract.AreaScope;
import com.housekeeping.platform.identity.application.contract.OperatorAreaScopeService;
import com.housekeeping.platform.operations.persistence.entity.OperationDailyMetricEntity;
import com.housekeeping.platform.operations.persistence.mapper.OperationDailyMetricMapper;
import com.housekeeping.platform.payment.application.contract.PaymentOperationsStatsService;
import com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow;
import com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow;
import com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow;
import com.housekeeping.platform.scheduling.application.contract.SchedulingOperationsStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营报表应用服务（docs/modules/13 第 7、8 节）。
 *
 * <p>实时报表直接聚合各模块统计契约；日报读取模型按
 * {@code operation_daily_metric} 区间查询。OPERATOR 只能看到授权区域的行
 * （全公司行 service_area_id = 0 不对 OPERATOR 开放），ADMIN 查看全部。</p>
 */
@Service
@RequiresPersistence
public class OperationsReportService {

    /** 报表区间上限（天），防止一次拉全表。 */
    private static final int MAX_RANGE_DAYS = 92;

    private final OperatorAreaScopeService areaScopeService;

    private final CurrentAccountResolver currentAccountResolver;

    private final BookingOperationsStatsService bookingStats;

    private final PaymentOperationsStatsService paymentStats;

    private final SchedulingOperationsStatsService schedulingStats;

    private final AftersalesOperationsStatsService aftersalesStats;

    private final OperationDailyMetricMapper dailyMetricMapper;

    public OperationsReportService(OperatorAreaScopeService areaScopeService,
                                   CurrentAccountResolver currentAccountResolver,
                                   BookingOperationsStatsService bookingStats,
                                   PaymentOperationsStatsService paymentStats,
                                   SchedulingOperationsStatsService schedulingStats,
                                   AftersalesOperationsStatsService aftersalesStats,
                                   OperationDailyMetricMapper dailyMetricMapper) {
        this.areaScopeService = areaScopeService;
        this.currentAccountResolver = currentAccountResolver;
        this.bookingStats = bookingStats;
        this.paymentStats = paymentStats;
        this.schedulingStats = schedulingStats;
        this.aftersalesStats = aftersalesStats;
        this.dailyMetricMapper = dailyMetricMapper;
    }

    /** 订单日报行。 */
    public record OrderReportRow(LocalDate statDate, Long serviceAreaId, long created,
                                 long completed, long canceled) {
    }

    /** 支付日报行（支付与退款按各自完成日归属）。 */
    public record PaymentReportRow(LocalDate statDate, Long serviceAreaId, long paidCount,
                                   BigDecimal paidAmount, BigDecimal refundAmount) {
    }

    /** 人员时段日报行（全公司口径）。 */
    public record WorkerReportRow(LocalDate statDate, long slotTotal, long slotBooked,
                                  BigDecimal utilizationPercent) {
    }

    /** 售后日报行。 */
    public record AfterSalesReportRow(LocalDate statDate, Long serviceAreaId,
                                      long complaintCount, long reworkCount) {
    }

    /** 日报读模型行。 */
    public record DailyMetricView(Long id, LocalDate metricDate, Long serviceAreaId,
                                  long ordersCreated, long ordersPaid, long ordersCompleted,
                                  long ordersCanceled, long ordersRescheduled,
                                  BigDecimal paidAmount, BigDecimal refundAmount,
                                  long autoAssignmentSuccess, long assignmentRejected,
                                  long assignmentTimeout, long manualAssignmentCount,
                                  long workerSlotTotal, long workerSlotBooked,
                                  long complaintCount, long reworkCount,
                                  int metricVersion, int recalcCount) {
    }

    /**
     * 订单报表（按创建日归属）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期升序的行
     */
    @Transactional(readOnly = true)
    public List<OrderReportRow> ordersReport(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return bookingStats.createdDailyStats(from, to).stream()
                .map(row -> new OrderReportRow(row.getStatDate(), row.getServiceAreaId(),
                        nvl(row.getTotal()), nvl(row.getCompleted()), nvl(row.getCanceled())))
                .sorted(java.util.Comparator.comparing(OrderReportRow::statDate))
                .toList();
    }

    /**
     * 支付报表（支付按 paid_at、退款按 refunded_at 归属）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期升序的行
     */
    @Transactional(readOnly = true)
    public List<PaymentReportRow> paymentsReport(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<String, PaymentReportRow> merged = new LinkedHashMap<>();
        for (BookingPaidStatRow row : bookingStats.paidDailyStats(from, to)) {
            String key = mergeKey(row.getStatDate(), row.getServiceAreaId());
            merged.computeIfAbsent(key, ignored -> new PaymentReportRow(
                            row.getStatDate(), row.getServiceAreaId(), 0L, BigDecimal.ZERO, BigDecimal.ZERO));
            PaymentReportRow target = merged.get(key);
            merged.put(key, new PaymentReportRow(target.statDate(), target.serviceAreaId(),
                    target.paidCount() + nvl(row.getCnt()),
                    target.paidAmount().add(valueOf(row.getAmount())), target.refundAmount()));
        }
        for (RefundDailyStatRow row : paymentStats.refundDailyStats(from, to)) {
            String key = mergeKey(row.getStatDate(), row.getServiceAreaId());
            merged.computeIfAbsent(key, ignored -> new PaymentReportRow(
                            row.getStatDate(), row.getServiceAreaId(), 0L, BigDecimal.ZERO, BigDecimal.ZERO));
            PaymentReportRow target = merged.get(key);
            merged.put(key, new PaymentReportRow(target.statDate(), target.serviceAreaId(),
                    target.paidCount(), target.paidAmount(),
                    target.refundAmount().add(valueOf(row.getAmount()))));
        }
        return merged.values().stream()
                .sorted(java.util.Comparator.comparing(PaymentReportRow::statDate))
                .toList();
    }

    /**
     * 人员时段报表（全公司口径）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期升序的行
     */
    @Transactional(readOnly = true)
    public List<WorkerReportRow> workersReport(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return schedulingStats.slotDailyStats(from, to).stream()
                .map(stat -> new WorkerReportRow(stat.statDate(), stat.total(), stat.booked(),
                                        utilization(stat.total(), stat.booked())))
                .sorted(java.util.Comparator.comparing(WorkerReportRow::statDate))
                .toList();
    }

    /**
     * 售后报表（按提交日归属，驳回工单不计入）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期升序的行
     */
    @Transactional(readOnly = true)
    public List<AfterSalesReportRow> afterSalesReport(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<String, AfterSalesReportRow> merged = new LinkedHashMap<>();
        for (AftersalesOperationsStatsService.AreaDailyCount row : aftersalesStats.complaintDaily(from, to)) {
            String key = mergeKey(row.statDate(), row.serviceAreaId());
            merged.computeIfAbsent(key, ignored -> new AfterSalesReportRow(
                    row.statDate(), row.serviceAreaId(), 0L, 0L));
            AfterSalesReportRow target = merged.get(key);
            merged.put(key, new AfterSalesReportRow(target.statDate(), target.serviceAreaId(),
                    target.complaintCount() + row.cnt(), target.reworkCount()));
        }
        for (AftersalesOperationsStatsService.AreaDailyCount row : aftersalesStats.reworkDaily(from, to)) {
            String key = mergeKey(row.statDate(), row.serviceAreaId());
            merged.computeIfAbsent(key, ignored -> new AfterSalesReportRow(
                    row.statDate(), row.serviceAreaId(), 0L, 0L));
            AfterSalesReportRow target = merged.get(key);
            merged.put(key, new AfterSalesReportRow(target.statDate(), target.serviceAreaId(),
                    target.complaintCount(), target.reworkCount() + row.cnt()));
        }
        return merged.values().stream()
                .sorted(java.util.Comparator.comparing(AfterSalesReportRow::statDate))
                .toList();
    }

    /**
     * 日报读模型分页查询。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @param page 页码
     * @param size 条数
     * @return 分页指标行
     */
    @Transactional(readOnly = true)
    public PageResult<DailyMetricView> dailyReport(LocalDate from, LocalDate to, int page, int size) {
        validateRange(from, to);
        AreaScope scope = requireScope();
        LambdaQueryWrapper<OperationDailyMetricEntity> wrapper =
                new LambdaQueryWrapper<OperationDailyMetricEntity>()
                        .between(OperationDailyMetricEntity::getMetricDate, from, to)
                        .in(!scope.allAreas(), OperationDailyMetricEntity::getServiceAreaId,
                                scope.areaIds())
                        .orderByDesc(OperationDailyMetricEntity::getMetricDate)
                        .orderByAsc(OperationDailyMetricEntity::getServiceAreaId);
        Page<OperationDailyMetricEntity> result =
                dailyMetricMapper.selectPage(new Page<>(page, size), wrapper);
        List<DailyMetricView> records = result.getRecords().stream()
                .map(OperationsReportService::toView)
                .toList();
        return new PageResult<>(records, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal());
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.REPORT_RANGE_INVALID);
        }
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

    private static String mergeKey(LocalDate statDate, Long serviceAreaId) {
        return statDate + "|" + (serviceAreaId == null ? 0L : serviceAreaId);
    }

    private static BigDecimal utilization(long total, long booked) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(booked)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static DailyMetricView toView(OperationDailyMetricEntity entity) {
        return new DailyMetricView(entity.getId(), entity.getMetricDate(),
                entity.getServiceAreaId(),
                nvlInt(entity.getOrdersCreated()), nvlInt(entity.getOrdersPaid()),
                nvlInt(entity.getOrdersCompleted()), nvlInt(entity.getOrdersCanceled()),
                nvlInt(entity.getOrdersRescheduled()),
                valueOf(entity.getPaidAmount()), valueOf(entity.getRefundAmount()),
                nvlInt(entity.getAutoAssignmentSuccess()), nvlInt(entity.getAssignmentRejected()),
                nvlInt(entity.getAssignmentTimeout()), nvlInt(entity.getManualAssignmentCount()),
                nvlInt(entity.getWorkerSlotTotal()), nvlInt(entity.getWorkerSlotBooked()),
                nvlInt(entity.getComplaintCount()), nvlInt(entity.getReworkCount()),
                entity.getMetricVersion() == null ? 1 : entity.getMetricVersion(),
                entity.getVersion() == null ? 0 : entity.getVersion().intValue());
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static long nvlInt(Integer value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal valueOf(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
