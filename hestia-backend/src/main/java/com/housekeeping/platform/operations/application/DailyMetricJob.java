package com.housekeeping.platform.operations.application;

import com.housekeeping.platform.aftersales.application.contract.AftersalesOperationsStatsService;
import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.application.contract.DispatchOperationsStatsService;
import com.housekeeping.platform.operations.domain.DailyMetricAssembler;
import com.housekeeping.platform.operations.persistence.entity.OperationDailyMetricEntity;
import com.housekeeping.platform.operations.persistence.mapper.OperationDailyMetricMapper;
import com.housekeeping.platform.payment.application.contract.PaymentOperationsStatsService;
import com.housekeeping.platform.scheduling.application.contract.SchedulingOperationsStatsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 日报读模型任务（docs/modules/13 第 8 节）。
 *
 * <p>聚合前一天各业务模块的公开统计契约，按 (metric_date, service_area_id)
 * UPSERT 到 {@code operation_daily_metric}，重复执行结果一致（幂等重算）。
 * 历史日期可手工重算（冒烟接口传入 asOf），口径版本记录在
 * {@code metric_version}。</p>
 */
@Component
@RequiresPersistence
public class DailyMetricJob {

    /** 业务统计统一按北京时间，与数据库 DATETIME 口径一致。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final BookingOperationsStatsService bookingStats;

    private final PaymentOperationsStatsService paymentStats;

    private final DispatchOperationsStatsService dispatchStats;

    private final SchedulingOperationsStatsService schedulingStats;

    private final AftersalesOperationsStatsService aftersalesStats;

    private final OperationDailyMetricMapper dailyMetricMapper;

    public DailyMetricJob(BookingOperationsStatsService bookingStats,
                          PaymentOperationsStatsService paymentStats,
                          DispatchOperationsStatsService dispatchStats,
                          SchedulingOperationsStatsService schedulingStats,
                          AftersalesOperationsStatsService aftersalesStats,
                          OperationDailyMetricMapper dailyMetricMapper) {
        this.bookingStats = bookingStats;
        this.paymentStats = paymentStats;
        this.dispatchStats = dispatchStats;
        this.schedulingStats = schedulingStats;
        this.aftersalesStats = aftersalesStats;
        this.dailyMetricMapper = dailyMetricMapper;
    }

    /**
     * 定时重算前一天指标。
     */
    @Scheduled(fixedDelayString = "${app.operations.daily-metric-sweep-interval-ms:600000}",
            initialDelayString = "${app.operations.daily-metric-initial-delay-ms:60000}")
    public void scheduledRecalculate() {
        runOnce(LocalDate.now(BUSINESS_ZONE).minusDays(1));
    }

    /**
     * 重算指定日期的日报指标（幂等）。
     *
     * @param metricDate 统计日期
     * @return UPSERT 的指标行数
     */
    @Transactional
    public int runOnce(LocalDate metricDate) {
        List<OperationDailyMetricEntity> metrics = DailyMetricAssembler.assemble(
                metricDate,
                bookingStats.createdDailyStats(metricDate, metricDate),
                bookingStats.paidDailyStats(metricDate, metricDate),
                bookingStats.rescheduledDailyStats(metricDate, metricDate),
                paymentStats.refundDailyStats(metricDate, metricDate),
                dispatchStats.autoSuccessDaily(metricDate, metricDate),
                dispatchStats.rejectedDaily(metricDate, metricDate),
                dispatchStats.timeoutDaily(metricDate, metricDate),
                dispatchStats.manualDaily(metricDate, metricDate),
                schedulingStats.slotDailyStats(metricDate, metricDate),
                aftersalesStats.complaintDaily(metricDate, metricDate),
                aftersalesStats.reworkDaily(metricDate, metricDate));
        int count = 0;
        for (OperationDailyMetricEntity metric : metrics) {
            count += dailyMetricMapper.upsert(metric);
        }
        return count;
    }
}
