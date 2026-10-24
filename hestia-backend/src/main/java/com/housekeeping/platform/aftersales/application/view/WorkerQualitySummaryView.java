package com.housekeeping.platform.aftersales.application.view;

import com.housekeeping.platform.aftersales.persistence.entity.WorkerQualityStatEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员质量汇总视图（docs/modules/11 第 8 节）。
 *
 * <p>派单评分与运营查看使用，不作为自动处罚依据。</p>
 *
 * @param qualityScore 0-100 派单质量分（average_rating / 5 × 100）
 */
public record WorkerQualitySummaryView(long workerId,
                                       int reviewCount,
                                       BigDecimal averageRating,
                                       int completedOrderCount,
                                       int complaintCount,
                                       int lateCount,
                                       int reworkCount,
                                       LocalDateTime lastCalculatedAt,
                                       int qualityScore) {

    /** 无汇总记录时的默认视图（派单中性分 60）。 */
    public static final int NEUTRAL_SCORE = 60;

    /**
     * 从汇总实体构建视图。
     *
     * @param stat 汇总实体
     * @return 汇总视图
     */
    public static WorkerQualitySummaryView from(WorkerQualityStatEntity stat) {
        int reviewCount = stat.getReviewCount() == null ? 0 : stat.getReviewCount();
        BigDecimal average = stat.getAverageRating() == null
                ? BigDecimal.ZERO : stat.getAverageRating();
        int score = reviewCount == 0 ? NEUTRAL_SCORE
                : average.multiply(BigDecimal.valueOf(20))
                        .setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        return new WorkerQualitySummaryView(
                stat.getWorkerId(), reviewCount, average,
                stat.getCompletedOrderCount() == null ? 0 : stat.getCompletedOrderCount(),
                stat.getComplaintCount() == null ? 0 : stat.getComplaintCount(),
                stat.getLateCount() == null ? 0 : stat.getLateCount(),
                stat.getReworkCount() == null ? 0 : stat.getReworkCount(),
                stat.getLastCalculatedAt(), Math.min(100, Math.max(0, score)));
    }

    /**
     * 空汇总视图（无记录人员）。
     *
     * @param workerId 人员 ID
     * @return 默认视图
     */
    public static WorkerQualitySummaryView empty(long workerId) {
        return new WorkerQualitySummaryView(workerId, 0, BigDecimal.ZERO, 0, 0, 0, 0,
                null, NEUTRAL_SCORE);
    }
}
