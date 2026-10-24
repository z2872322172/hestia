package com.housekeeping.platform.aftersales.application.view;

import com.housekeeping.platform.aftersales.persistence.entity.ReviewDimensionScoreEntity;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewEvidenceEntity;
import com.housekeeping.platform.aftersales.persistence.entity.ServiceReviewEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图：评价主记录 + 维度分数 + 附件。
 */
public record ReviewView(long id,
                         String reviewNo,
                         long bookingId,
                         long customerId,
                         long workerId,
                         BigDecimal overallScore,
                         String content,
                         String status,
                         String hiddenReason,
                         LocalDateTime createdAt,
                         List<DimensionScoreView> dimensions,
                         List<Long> fileIds) {

    /**
     * 聚合构建视图。
     *
     * @param review     评价实体
     * @param scores     维度分数
     * @param evidences  附件
     * @return 评价视图
     */
    public static ReviewView from(ServiceReviewEntity review,
                                  List<ReviewDimensionScoreEntity> scores,
                                  List<ReviewEvidenceEntity> evidences) {
        return new ReviewView(
                review.getId(), review.getReviewNo(), review.getBookingId(),
                review.getCustomerId(), review.getWorkerId(), review.getOverallScore(),
                review.getContent(), review.getStatus(), review.getHiddenReason(),
                review.getCreatedAt(),
                scores.stream().map(DimensionScoreView::from).toList(),
                evidences.stream().map(ReviewEvidenceEntity::getFileId).toList());
    }

    /** 维度分数视图。 */
    public record DimensionScoreView(String dimensionCode, BigDecimal score) {

        /** 从实体构建视图。 */
        public static DimensionScoreView from(ReviewDimensionScoreEntity entity) {
            return new DimensionScoreView(entity.getDimensionCode(), entity.getScore());
        }
    }
}
