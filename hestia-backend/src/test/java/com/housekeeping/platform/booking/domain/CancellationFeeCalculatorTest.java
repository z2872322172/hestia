package com.housekeeping.platform.booking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取消费用计算测试（docs/modules/07 第 10.2、10.3 节）。
 *
 * <p>规则：距开始 ≥ 免费取消小时数 → 零费用；
 * 不足 → 违约金 = max(固定金额, 应付 × 费率) 向上取整到分，封顶应付金额。</p>
 */
class CancellationFeeCalculatorTest {

    /** 测试用规则：24 小时内收费，费率 30%，固定 50 元。 */
    private static final CancellationFeePolicy POLICY =
            new CancellationFeePolicy(24, new BigDecimal("0.3"), new BigDecimal("50"));

    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 10, 9, 0);

    @Test
    @DisplayName("距开始恰好 24 小时：免费取消")
    void freeAtBoundary() {
        LocalDateTime now = START.minusHours(24);
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("200.00"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("0.00");
        assertThat(result.refundableAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("远超免费时长：免费取消")
    void freeWellAhead() {
        LocalDateTime now = START.minusDays(7);
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("200.00"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("0");
        assertThat(result.refundableAmount()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("不足免费时长：费率计算结果高于固定金额时取费率")
    void rateFeeDominates() {
        LocalDateTime now = START.minusHours(10);
        // 200 × 0.3 = 60 > 50
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("200.00"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("60.00");
        assertThat(result.refundableAmount()).isEqualByComparingTo("140.00");
    }

    @Test
    @DisplayName("不足免费时长：固定金额高于费率结果时取固定金额")
    void fixedFeeDominates() {
        LocalDateTime now = START.minusHours(2);
        // 100 × 0.3 = 30 < 50
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("100.00"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("50.00");
        assertThat(result.refundableAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("费率结果带小数：向上取整到分")
    void feeRoundsUp() {
        LocalDateTime now = START.minusHours(10);
        // 333.33 × 0.3 = 99.999 → 100.00（> 固定 50，取费率）
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("333.33"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("100.00");
        assertThat(result.refundableAmount()).isEqualByComparingTo("233.33");
    }

    @Test
    @DisplayName("违约金不超过应付金额：小额订单封顶")
    void feeCappedAtPayable() {
        LocalDateTime now = START.minusHours(1);
        // 固定 50 > 应付 30：封顶 30
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("30.00"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("30.00");
        assertThat(result.refundableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("已开始后取消：全额违约（费率封顶应付）")
    void afterStart() {
        LocalDateTime now = START.plusMinutes(30);
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("200.00"), START, now, POLICY);
        assertThat(result.feeAmount()).isEqualByComparingTo("60.00");
        assertThat(result.refundableAmount()).isEqualByComparingTo("140.00");
    }

    @Test
    @DisplayName("免费时长为 0：任意时刻取消都收费")
    void zeroFreeHours() {
        CancellationFeePolicy policy = new CancellationFeePolicy(0,
                new BigDecimal("0.3"), new BigDecimal("50"));
        LocalDateTime now = START.minusDays(3);
        var result = CancellationFeeCalculator.calculate(
                new BigDecimal("200.00"), START, now, policy);
        assertThat(result.feeAmount()).isEqualByComparingTo("60.00");
    }
}
