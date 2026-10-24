package com.housekeeping.platform.dispatch.application.view;

import com.housekeeping.platform.dispatch.persistence.entity.DispatchCandidateEntity;

import java.math.BigDecimal;

/**
 * 派单候选视图（运营端）：评分快照与名次，不含客户敏感信息。
 */
public record DispatchCandidateView(long id,
                                    long workerId,
                                    int rankNo,
                                    BigDecimal totalScore,
                                    String scoreJson,
                                    String status) {

    /** 从候选实体构建视图。 */
    public static DispatchCandidateView from(DispatchCandidateEntity entity) {
        return new DispatchCandidateView(
                entity.getId(),
                entity.getWorkerId(),
                entity.getRankNo(),
                entity.getTotalScore(),
                entity.getScoreJson(),
                entity.getStatus());
    }
}
