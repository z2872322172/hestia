package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.aftersales.application.contract.AftersalesOperationsStatsService;
import com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow;
import com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow;
import com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow;
import com.housekeeping.platform.operations.persistence.entity.OperationDailyMetricEntity;
import com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow;
import com.housekeeping.platform.scheduling.application.contract.SchedulingOperationsStatsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日报装配器（docs/modules/13 第 8 节）：把各模块统计契约的原始行合并为
 * {@code operation_daily_metric} 指标行。纯函数，无副作用，便于单测口径。
 *
 * <p>行归属规则：</p>
 * <ul>
 *   <li>订单 / 支付 / 改期 / 退款 / 投诉 / 返工携带区域 → 按区域成行；</li>
 *   <li>派单行为与时段为全公司口径 → 只进全公司行（service_area_id = 0）；</li>
 *   <li>全公司行 = 各区域行汇总 + 全公司口径计数，无任何数据时不成行。</li>
 * </ul>
 */
public final class DailyMetricAssembler {

    /** 当前统计口径版本（口径变更时递增，历史行保留旧版本号）。 */
    public static final int METRIC_VERSION = 1;

    private DailyMetricAssembler() {
    }

    /**
     * 装配指定日期的指标行。
     *
     * @param metricDate      统计日期
     * @param createdRows     订单创建 / 完成 / 取消行（按创建日）
     * @param paidRows        支付行（按支付完成日）
     * @param rescheduledRows 改期行（按改期申请日）
     * @param refundRows      退款行（按退款完成日）
     * @param autoSuccessRows 自动派单接单成功行（全公司口径）
     * @param rejectedRows    拒单行（全公司口径）
     * @param timeoutRows     接单超时行（全公司口径）
     * @param manualRows      人工派单行（全公司口径）
     * @param slotRows        人员时段行（全公司口径）
     * @param complaintRows   投诉 / 售后工单行
     * @param reworkRows      返工工单行
     * @return 指标行列表（区域行 + 全公司行），可为空列表
     */
    public static List<OperationDailyMetricEntity> assemble(
            LocalDate metricDate,
            List<BookingDailyStatRow> createdRows,
            List<BookingPaidStatRow> paidRows,
            List<BookingDailyStatRow> rescheduledRows,
            List<RefundDailyStatRow> refundRows,
            List<AssignmentDateCountRow> autoSuccessRows,
            List<AssignmentDateCountRow> rejectedRows,
            List<AssignmentDateCountRow> timeoutRows,
            List<AssignmentDateCountRow> manualRows,
            List<SchedulingOperationsStatsService.SlotDailyStat> slotRows,
            List<AftersalesOperationsStatsService.AreaDailyCount> complaintRows,
            List<AftersalesOperationsStatsService.AreaDailyCount> reworkRows) {

        Map<Long, AreaAccumulator> areas = new LinkedHashMap<>();
        CompanyAccumulator company = new CompanyAccumulator();

        for (BookingDailyStatRow row : rowsOf(createdRows)) {
            if (!metricDate.equals(row.getStatDate())) {
                continue;
            }
            AreaAccumulator acc = areaAcc(areas, row.getServiceAreaId());
            acc.ordersCreated += nvl(row.getTotal());
            acc.ordersCompleted += nvl(row.getCompleted());
            acc.ordersCanceled += nvl(row.getCanceled());
        }
        for (BookingPaidStatRow row : rowsOf(paidRows)) {
            if (!metricDate.equals(row.getStatDate())) {
                continue;
            }
            AreaAccumulator acc = areaAcc(areas, row.getServiceAreaId());
            acc.ordersPaid += nvl(row.getCnt());
            acc.paidAmount = acc.paidAmount.add(valueOf(row.getAmount()));
        }
        for (BookingDailyStatRow row : rowsOf(rescheduledRows)) {
            if (!metricDate.equals(row.getStatDate())) {
                continue;
            }
            areaAcc(areas, row.getServiceAreaId()).ordersRescheduled += nvl(row.getTotal());
        }
        for (RefundDailyStatRow row : rowsOf(refundRows)) {
            if (!metricDate.equals(row.getStatDate())) {
                continue;
            }
            areaAcc(areas, row.getServiceAreaId()).refundAmount =
                    areaAcc(areas, row.getServiceAreaId()).refundAmount.add(valueOf(row.getAmount()));
        }
        for (AftersalesOperationsStatsService.AreaDailyCount row : rowsOf(complaintRows)) {
            if (!metricDate.equals(row.statDate())) {
                continue;
            }
            areaAcc(areas, row.serviceAreaId()).complaintCount += row.cnt();
        }
        for (AftersalesOperationsStatsService.AreaDailyCount row : rowsOf(reworkRows)) {
            if (!metricDate.equals(row.statDate())) {
                continue;
            }
            areaAcc(areas, row.serviceAreaId()).reworkCount += row.cnt();
        }

        for (AssignmentDateCountRow row : rowsOf(autoSuccessRows)) {
            if (metricDate.equals(row.getStatDate())) {
                company.autoAssignmentSuccess += nvl(row.getCnt());
            }
        }
        for (AssignmentDateCountRow row : rowsOf(rejectedRows)) {
            if (metricDate.equals(row.getStatDate())) {
                company.assignmentRejected += nvl(row.getCnt());
            }
        }
        for (AssignmentDateCountRow row : rowsOf(timeoutRows)) {
            if (metricDate.equals(row.getStatDate())) {
                company.assignmentTimeout += nvl(row.getCnt());
            }
        }
        for (AssignmentDateCountRow row : rowsOf(manualRows)) {
            if (metricDate.equals(row.getStatDate())) {
                company.manualAssignmentCount += nvl(row.getCnt());
            }
        }
        for (SchedulingOperationsStatsService.SlotDailyStat stat : rowsOf(slotRows)) {
            if (metricDate.equals(stat.statDate())) {
                company.workerSlotTotal += stat.total();
                company.workerSlotBooked += stat.booked();
            }
        }

        List<OperationDailyMetricEntity> result = new ArrayList<>();
        for (Map.Entry<Long, AreaAccumulator> entry : areas.entrySet()) {
            Long areaId = entry.getKey();
            if (areaId == null || areaId <= 0) {
                continue;
            }
            AreaAccumulator acc = entry.getValue();
            OperationDailyMetricEntity metric = blank(metricDate, areaId);
            acc.applyTo(metric);
            result.add(metric);
        }

        if (!areas.isEmpty() || company.hasAny()) {
            OperationDailyMetricEntity metric = blank(metricDate, 0L);
            company.applyTo(metric, areas.values());
            result.add(metric);
        }
        return result;
    }

    private static AreaAccumulator areaAcc(Map<Long, AreaAccumulator> areas, Long areaId) {
        return areas.computeIfAbsent(areaId, key -> new AreaAccumulator());
    }

    private static OperationDailyMetricEntity blank(LocalDate metricDate, Long serviceAreaId) {
        OperationDailyMetricEntity metric = new OperationDailyMetricEntity();
        metric.setMetricDate(metricDate);
        metric.setServiceAreaId(serviceAreaId);
        metric.setOrdersCreated(0);
        metric.setOrdersPaid(0);
        metric.setOrdersCompleted(0);
        metric.setOrdersCanceled(0);
        metric.setOrdersRescheduled(0);
        metric.setPaidAmount(BigDecimal.ZERO);
        metric.setRefundAmount(BigDecimal.ZERO);
        metric.setAutoAssignmentSuccess(0);
        metric.setAssignmentRejected(0);
        metric.setAssignmentTimeout(0);
        metric.setManualAssignmentCount(0);
        metric.setWorkerSlotTotal(0);
        metric.setWorkerSlotBooked(0);
        metric.setComplaintCount(0);
        metric.setReworkCount(0);
        metric.setLateCount(0);
        metric.setMetricVersion(METRIC_VERSION);
        return metric;
    }

    private static <T> List<T> rowsOf(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal valueOf(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 单区域累计器。 */
    private static final class AreaAccumulator {

        private long ordersCreated;
        private long ordersPaid;
        private long ordersCompleted;
        private long ordersCanceled;
        private long ordersRescheduled;
        private long complaintCount;
        private long reworkCount;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private BigDecimal refundAmount = BigDecimal.ZERO;

        private void applyTo(OperationDailyMetricEntity metric) {
            metric.setOrdersCreated((int) ordersCreated);
            metric.setOrdersPaid((int) ordersPaid);
            metric.setOrdersCompleted((int) ordersCompleted);
            metric.setOrdersCanceled((int) ordersCanceled);
            metric.setOrdersRescheduled((int) ordersRescheduled);
            metric.setComplaintCount((int) complaintCount);
            metric.setReworkCount((int) reworkCount);
            metric.setPaidAmount(paidAmount);
            metric.setRefundAmount(refundAmount);
        }
    }

    /** 全公司累计器（派单行为、时段；区域汇总在 applyTo 时现算）。 */
    private static final class CompanyAccumulator {

        private long autoAssignmentSuccess;
        private long assignmentRejected;
        private long assignmentTimeout;
        private long manualAssignmentCount;
        private long workerSlotTotal;
        private long workerSlotBooked;

        private boolean hasAny() {
            return autoAssignmentSuccess > 0 || assignmentRejected > 0
                    || assignmentTimeout > 0 || manualAssignmentCount > 0
                    || workerSlotTotal > 0 || workerSlotBooked > 0;
        }

        private void applyTo(OperationDailyMetricEntity metric, Iterable<AreaAccumulator> areas) {
            long ordersCreated = 0;
            long ordersPaid = 0;
            long ordersCompleted = 0;
            long ordersCanceled = 0;
            long ordersRescheduled = 0;
            long complaintCount = 0;
            long reworkCount = 0;
            BigDecimal paidAmount = BigDecimal.ZERO;
            BigDecimal refundAmount = BigDecimal.ZERO;
            for (AreaAccumulator acc : areas) {
                ordersCreated += acc.ordersCreated;
                ordersPaid += acc.ordersPaid;
                ordersCompleted += acc.ordersCompleted;
                ordersCanceled += acc.ordersCanceled;
                ordersRescheduled += acc.ordersRescheduled;
                complaintCount += acc.complaintCount;
                reworkCount += acc.reworkCount;
                paidAmount = paidAmount.add(acc.paidAmount);
                refundAmount = refundAmount.add(acc.refundAmount);
            }
            metric.setOrdersCreated((int) ordersCreated);
            metric.setOrdersPaid((int) ordersPaid);
            metric.setOrdersCompleted((int) ordersCompleted);
            metric.setOrdersCanceled((int) ordersCanceled);
            metric.setOrdersRescheduled((int) ordersRescheduled);
            metric.setComplaintCount((int) complaintCount);
            metric.setReworkCount((int) reworkCount);
            metric.setPaidAmount(paidAmount);
            metric.setRefundAmount(refundAmount);
            metric.setAutoAssignmentSuccess((int) autoAssignmentSuccess);
            metric.setAssignmentRejected((int) assignmentRejected);
            metric.setAssignmentTimeout((int) assignmentTimeout);
            metric.setManualAssignmentCount((int) manualAssignmentCount);
            metric.setWorkerSlotTotal((int) workerSlotTotal);
            metric.setWorkerSlotBooked((int) workerSlotBooked);
        }
    }
}
