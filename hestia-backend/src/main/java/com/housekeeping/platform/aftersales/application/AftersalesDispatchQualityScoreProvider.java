package com.housekeeping.platform.aftersales.application;

import com.housekeeping.platform.aftersales.persistence.entity.WorkerQualityStatEntity;
import com.housekeeping.platform.aftersales.persistence.mapper.WorkerQualityStatMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.application.DispatchQualityScoreProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

/**
 * 派单质量分提供者——售后模块实现（docs/modules/11 第 8 节）。
 *
 * <p>读取 {@code worker_quality_stat.average_rating} 折算 0-100 分：
 * {@code average_rating / 5 × 100}。无汇总记录或尚无可见评价时返回
 * 中性分 60，避免新人员因数据缺失被压制。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class AftersalesDispatchQualityScoreProvider implements DispatchQualityScoreProvider {

    /** 中性分：0-100 尺度下取 60（略高于及格线的无偏默认）。 */
    static final int NEUTRAL_SCORE = 60;

    private final WorkerQualityStatMapper statMapper;

    public AftersalesDispatchQualityScoreProvider(WorkerQualityStatMapper statMapper) {
        this.statMapper = statMapper;
    }

    @Override
    public int qualityScore(long workerId) {
        WorkerQualityStatEntity stat = statMapper.selectByWorkerId(workerId);
        if (stat == null || stat.getReviewCount() == null || stat.getReviewCount() == 0
                || stat.getAverageRating() == null) {
            return NEUTRAL_SCORE;
        }
        int score = stat.getAverageRating()
                .multiply(java.math.BigDecimal.valueOf(20))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.min(100, Math.max(0, score));
    }
}
