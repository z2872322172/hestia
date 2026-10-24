package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.operations.persistence.entity.OperationDailyMetricEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日报装配口径测试（docs/modules/13 第 20.1 节"指标统计口径"）。
 *
 * <p>口径要点：区域行只含区域归属指标；派单 / 时段为全公司口径只进公司行；
 * 公司行 = 区域汇总 + 全公司口径；非目标日期的输入行必须被过滤。</p>
 */
class DailyMetricAssemblerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 10, 22);

    @Test
    @DisplayName("区域行 + 公司行：口径与汇总正确")
    void assemblesAreaAndCompanyRows() {
        List<OperationDailyMetricEntity> metrics = DailyMetricAssembler.assemble(
                DATE,
                List.of(row(OTHER_DATE, 1L, 5L, 1L, 1L), row(DATE, 1L, 10L, 3L, 2L)),
                List.of(paidRow(DATE, 1L, 8L, "800.00")),
                List.of(rescheduleRow(DATE, 1L, 1L)),
                List.of(refundRow(DATE, 1L, "150.00")),
                List.of(countRow(DATE, 5L)),
                List.of(countRow(DATE, 1L)),
                List.of(countRow(DATE, 2L)),
                List.of(countRow(DATE, 1L)),
                List.of(new SchedulingSlot(DATE, 50, 20).toStat()),
                List.of(new AreaCount(DATE, 1L, 2).toRecord()),
                List.of(new AreaCount(DATE, 1L, 1).toRecord()));

        assertThat(metrics).hasSize(2);

        OperationDailyMetricEntity area = metrics.stream()
                .filter(metric -> metric.getServiceAreaId() == 1L)
                .findFirst().orElseThrow();
        assertThat(area.getMetricDate()).isEqualTo(DATE);
        assertThat(area.getOrdersCreated()).isEqualTo(10);
        assertThat(area.getOrdersCompleted()).isEqualTo(3);
        assertThat(area.getOrdersCanceled()).isEqualTo(2);
        assertThat(area.getOrdersPaid()).isEqualTo(8);
        assertThat(area.getPaidAmount()).isEqualByComparingTo("800.00");
        assertThat(area.getOrdersRescheduled()).isEqualTo(1);
        assertThat(area.getRefundAmount()).isEqualByComparingTo("150.00");
        assertThat(area.getComplaintCount()).isEqualTo(2);
        assertThat(area.getReworkCount()).isEqualTo(1);
        // 区域行不含全公司口径的派单 / 时段计数
        assertThat(area.getAutoAssignmentSuccess()).isZero();
        assertThat(area.getWorkerSlotTotal()).isZero();

        OperationDailyMetricEntity company = metrics.stream()
                .filter(metric -> metric.getServiceAreaId() == 0L)
                .findFirst().orElseThrow();
        assertThat(company.getOrdersCreated()).isEqualTo(10);
        assertThat(company.getOrdersPaid()).isEqualTo(8);
        assertThat(company.getPaidAmount()).isEqualByComparingTo("800.00");
        assertThat(company.getRefundAmount()).isEqualByComparingTo("150.00");
        assertThat(company.getAutoAssignmentSuccess()).isEqualTo(5);
        assertThat(company.getAssignmentRejected()).isEqualTo(1);
        assertThat(company.getAssignmentTimeout()).isEqualTo(2);
        assertThat(company.getManualAssignmentCount()).isEqualTo(1);
        assertThat(company.getWorkerSlotTotal()).isEqualTo(50);
        assertThat(company.getWorkerSlotBooked()).isEqualTo(20);
        assertThat(company.getComplaintCount()).isEqualTo(2);
        assertThat(company.getMetricVersion())
                .isEqualTo(DailyMetricAssembler.METRIC_VERSION);
    }

    @Test
    @DisplayName("仅有派单数据的日期：只成公司行")
    void assemblesCompanyOnlyRow() {
        List<OperationDailyMetricEntity> metrics = DailyMetricAssembler.assemble(
                DATE,
                List.of(), List.of(), List.of(), List.of(),
                List.of(countRow(DATE, 3L)),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0).getServiceAreaId()).isZero();
        assertThat(metrics.get(0).getAutoAssignmentSuccess()).isEqualTo(3);
    }

    @Test
    @DisplayName("无任何数据：不成行")
    void assemblesNothingWithoutData() {
        List<OperationDailyMetricEntity> metrics = DailyMetricAssembler.assemble(
                DATE,
                List.of(row(OTHER_DATE, 1L, 5L, 1L, 1L)),
                List.of(), List.of(), List.of(),
                List.of(countRow(OTHER_DATE, 3L)),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        assertThat(metrics).isEmpty();
    }

    // ------------------------------------------------------------------
    // 行构造辅助
    // ------------------------------------------------------------------

    private static com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow row(
            LocalDate statDate, Long areaId, Long total, Long completed, Long canceled) {
        com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow row =
                new com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow();
        row.setStatDate(statDate);
        row.setServiceAreaId(areaId);
        row.setTotal(total);
        row.setCompleted(completed);
        row.setCanceled(canceled);
        return row;
    }

    private static com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow paidRow(
            LocalDate statDate, Long areaId, Long cnt, String amount) {
        com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow row =
                new com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow();
        row.setStatDate(statDate);
        row.setServiceAreaId(areaId);
        row.setCnt(cnt);
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    private static com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow rescheduleRow(
            LocalDate statDate, Long areaId, Long total) {
        return row(statDate, areaId, total, null, null);
    }

    private static com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow refundRow(
            LocalDate statDate, Long areaId, String amount) {
        com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow row =
                new com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow();
        row.setStatDate(statDate);
        row.setServiceAreaId(areaId);
        row.setCnt(1L);
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    private static com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow countRow(
            LocalDate statDate, Long cnt) {
        com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow row =
                new com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow();
        row.setStatDate(statDate);
        row.setCnt(cnt);
        return row;
    }

    /** 时段统计行构造辅助（契约嵌套 record 的薄封装）。 */
    private record SchedulingSlot(LocalDate statDate, long total, long booked) {
        private com.housekeeping.platform.scheduling.application.contract
                .SchedulingOperationsStatsService.SlotDailyStat toStat() {
            return new com.housekeeping.platform.scheduling.application.contract
                    .SchedulingOperationsStatsService.SlotDailyStat(statDate, total, booked);
        }
    }

    /** 区域计数行构造辅助（契约嵌套 record 的薄封装）。 */
    private record AreaCount(LocalDate statDate, Long serviceAreaId, long cnt) {
        private com.housekeeping.platform.aftersales.application.contract
                .AftersalesOperationsStatsService.AreaDailyCount toRecord() {
            return new com.housekeeping.platform.aftersales.application.contract
                    .AftersalesOperationsStatsService.AreaDailyCount(statDate, serviceAreaId, cnt);
        }
    }
}
