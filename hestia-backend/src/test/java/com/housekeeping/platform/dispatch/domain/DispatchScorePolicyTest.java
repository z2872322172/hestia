package com.housekeeping.platform.dispatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 派单评分策略测试（docs/modules/08 第 7 节）。
 */
class DispatchScorePolicyTest {

    @Test
    @DisplayName("默认权重下总分 = Σ 分项 × 权重 / 100，保留 3 位小数")
    void defaultWeightsScore() {
        DispatchScorePolicy.ScoreInputs inputs =
                new DispatchScorePolicy.ScoreInputs(100, 80, 60, 40, 20, 0);
        DispatchScorePolicy.ScoreResult result =
                DispatchScorePolicy.score(inputs, DispatchScorePolicy.ScoreWeights.DEFAULT);
        // 100*25 + 80*20 + 60*20 + 40*15 + 20*10 + 0*10 = 2500+1600+1200+600+200 = 6100 → 61.000
        assertThat(result.totalScore()).isEqualByComparingTo(BigDecimal.valueOf(61.000));
    }

    @Test
    @DisplayName("全部满分得 100，全部零分得 0")
    void boundaryScores() {
        DispatchScorePolicy.ScoreInputs allMax =
                new DispatchScorePolicy.ScoreInputs(100, 100, 100, 100, 100, 100);
        DispatchScorePolicy.ScoreInputs allZero =
                new DispatchScorePolicy.ScoreInputs(0, 0, 0, 0, 0, 0);
        assertThat(DispatchScorePolicy.score(allMax, DispatchScorePolicy.ScoreWeights.DEFAULT)
                .totalScore()).isEqualByComparingTo(BigDecimal.valueOf(100.000));
        assertThat(DispatchScorePolicy.score(allZero, DispatchScorePolicy.ScoreWeights.DEFAULT)
                .totalScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("四舍五入到 3 位小数")
    void rounding() {
        // 33*25 + 33*20 + 33*20 + 33*15 + 33*10 + 33*10 = 33*100 = 3300 → 33.000
        // 换一组非整值：97*25+91*20+88*20+76*15+65*10+54*10 = 2425+1820+1760+1140+650+540 = 8335 → 83.350
        DispatchScorePolicy.ScoreInputs inputs =
                new DispatchScorePolicy.ScoreInputs(97, 91, 88, 76, 65, 54);
        DispatchScorePolicy.ScoreResult result =
                DispatchScorePolicy.score(inputs, DispatchScorePolicy.ScoreWeights.DEFAULT);
        assertThat(result.totalScore()).isEqualByComparingTo(BigDecimal.valueOf(83.350));
    }

    @Test
    @DisplayName("拆解键序稳定且与输入一致")
    void breakdownKeys() {
        DispatchScorePolicy.ScoreInputs inputs =
                new DispatchScorePolicy.ScoreInputs(10, 20, 30, 40, 50, 60);
        DispatchScorePolicy.ScoreResult result =
                DispatchScorePolicy.score(inputs, DispatchScorePolicy.ScoreWeights.DEFAULT);
        assertThat(result.breakdown().keySet()).containsExactly(
                DispatchScorePolicy.KEY_AREA, DispatchScorePolicy.KEY_WORKLOAD,
                DispatchScorePolicy.KEY_QUALITY, DispatchScorePolicy.KEY_SKILL,
                DispatchScorePolicy.KEY_CONTINUITY, DispatchScorePolicy.KEY_PREFERENCE);
        assertThat(result.breakdown().values()).containsExactly(10, 20, 30, 40, 50, 60);
    }

    @Test
    @DisplayName("自定义权重覆盖生效")
    void customWeights() {
        DispatchScorePolicy.ScoreInputs inputs =
                new DispatchScorePolicy.ScoreInputs(80, 0, 0, 0, 0, 0);
        DispatchScorePolicy.ScoreWeights weights =
                new DispatchScorePolicy.ScoreWeights(50, 10, 10, 10, 10, 10);
        // 80*50/100 = 40
        assertThat(DispatchScorePolicy.score(inputs, weights).totalScore())
                .isEqualByComparingTo(BigDecimal.valueOf(40.000));
    }
}
