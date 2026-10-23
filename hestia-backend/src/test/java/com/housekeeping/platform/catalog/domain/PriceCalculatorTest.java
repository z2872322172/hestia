package com.housekeeping.platform.catalog.domain;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 计价器测试。
 *
 * <p>计价是金额的唯一事实来源，所以这里覆盖的不是"能跑通"，而是边界：
 * 档位左右端点、加项累加、四舍五入、数量越界、档位缺失与重叠。</p>
 */
class PriceCalculatorTest {

    private static PriceRequest fixed(BigDecimal basePrice, BigDecimal min, BigDecimal max,
                                      BigDecimal quantity, List<PricedOptionValue> selections) {
        return new PriceRequest(PricingMode.FIXED, basePrice, 240, min, max, quantity,
                null, selections, List.of());
    }

    private static PriceRequest areaTier(BigDecimal measure, List<PricedTier> tiers,
                                         List<PricedOptionValue> selections) {
        return new PriceRequest(PricingMode.AREA_TIER, BigDecimal.ZERO, 300,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, measure, selections, tiers);
    }

    @Nested
    @DisplayName("固定价")
    class FixedPrice {

        @Test
        @DisplayName("基础价 × 数量")
        void multipliesBasePriceByQuantity() {
            PriceBreakdown breakdown = PriceCalculator.calculate(fixed(
                    new BigDecimal("199.00"), BigDecimal.ONE, new BigDecimal("5"),
                    new BigDecimal("2"), List.of()));

            assertThat(breakdown.baseAmount()).isEqualByComparingTo("398.00");
            assertThat(breakdown.payableAmount()).isEqualByComparingTo("398.00");
            assertThat(breakdown.durationMinutes()).isEqualTo(240);
        }

        @Test
        @DisplayName("基础价先按两位舍入再乘数量：0.005 → 0.01")
        void roundsBasePriceBeforeMultiplying() {
            PriceBreakdown breakdown = PriceCalculator.calculate(fixed(
                    new BigDecimal("0.005"), BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, List.of()));

            assertThat(breakdown.baseAmount()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("数量超出上限被拒绝")
        void rejectsQuantityAboveMax() {
            assertThatThrownBy(() -> PriceCalculator.calculate(fixed(
                    new BigDecimal("199.00"), BigDecimal.ONE, new BigDecimal("2"),
                    new BigDecimal("3"), List.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.SERVICE_QUANTITY_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("数量低于下限被拒绝")
        void rejectsQuantityBelowMin() {
            assertThatThrownBy(() -> PriceCalculator.calculate(fixed(
                    new BigDecimal("199.00"), new BigDecimal("2"), null,
                    BigDecimal.ONE, List.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.SERVICE_QUANTITY_OUT_OF_RANGE);
        }
    }

    @Nested
    @DisplayName("面积阶梯")
    class AreaTier {

        private final List<PricedTier> tiers = List.of(
                new PricedTier(new BigDecimal("0"), new BigDecimal("60"), new BigDecimal("399.00"), null),
                new PricedTier(new BigDecimal("60"), new BigDecimal("90"), new BigDecimal("499.00"), null),
                new PricedTier(new BigDecimal("90"), new BigDecimal("120"), new BigDecimal("599.00"), null),
                new PricedTier(new BigDecimal("120"), null, null, new BigDecimal("6.00")));

        @Test
        @DisplayName("区间左闭右开：60 落在 [60,90) 而不是 [0,60)")
        void boundaryBelongsToUpperTier() {
            assertThat(PriceCalculator.calculate(areaTier(new BigDecimal("60"), tiers, List.of()))
                    .baseAmount()).isEqualByComparingTo("499.00");
            assertThat(PriceCalculator.calculate(areaTier(new BigDecimal("59.99"), tiers, List.of()))
                    .baseAmount()).isEqualByComparingTo("399.00");
            assertThat(PriceCalculator.calculate(areaTier(new BigDecimal("89.99"), tiers, List.of()))
                    .baseAmount()).isEqualByComparingTo("499.00");
        }

        @Test
        @DisplayName("最高档按单价乘面积：120 × 6 = 720")
        void openEndedTierUsesUnitPrice() {
            PriceBreakdown breakdown = PriceCalculator.calculate(
                    areaTier(new BigDecimal("120"), tiers, List.of()));

            assertThat(breakdown.baseAmount()).isEqualByComparingTo("720.00");
        }

        @Test
        @DisplayName("没有档位覆盖该面积时报阶梯缺失")
        void reportsMissingTier() {
            List<PricedTier> limited = List.of(
                    new PricedTier(new BigDecimal("0"), new BigDecimal("60"), new BigDecimal("399.00"), null));

            assertThatThrownBy(() -> PriceCalculator.calculate(
                    areaTier(new BigDecimal("80"), limited, List.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.SERVICE_PRICE_TIER_NOT_FOUND);
        }

        @Test
        @DisplayName("档位重叠时拒绝计价，而不是取第一个")
        void rejectsOverlappingTiers() {
            List<PricedTier> overlapping = List.of(
                    new PricedTier(new BigDecimal("0"), new BigDecimal("60"), new BigDecimal("399.00"), null),
                    new PricedTier(new BigDecimal("50"), new BigDecimal("90"), new BigDecimal("499.00"), null));

            assertThatThrownBy(() -> PriceCalculator.calculate(
                    areaTier(new BigDecimal("55"), overlapping, List.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.SERVICE_PRICE_TIER_CONFLICT);
        }

        @Test
        @DisplayName("相邻档位不算重叠")
        void adjacentTiersAreNotOverlapping() {
            List<PricedTier> adjacent = List.of(
                    new PricedTier(new BigDecimal("0"), new BigDecimal("60"), new BigDecimal("399.00"), null),
                    new PricedTier(new BigDecimal("60"), new BigDecimal("90"), new BigDecimal("499.00"), null));

            PriceCalculator.assertNoOverlap(adjacent);
        }
    }

    @Nested
    @DisplayName("加项")
    class Addons {

        @Test
        @DisplayName("加项金额累加、时长增量累加")
        void accumulatesPriceAndDuration() {
            List<PricedOptionValue> selections = List.of(
                    new PricedOptionValue("ADDON", "增值服务", "FRIDGE", "冰箱内部清洗",
                            new BigDecimal("80.00"), 30, null),
                    new PricedOptionValue("ADDON", "增值服务", "WINDOW", "玻璃清洗",
                            new BigDecimal("60.00"), 20, null));

            PriceBreakdown breakdown = PriceCalculator.calculate(fixed(
                    new BigDecimal("199.00"), BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, selections));

            assertThat(breakdown.baseAmount()).isEqualByComparingTo("199.00");
            assertThat(breakdown.addonAmount()).isEqualByComparingTo("140.00");
            assertThat(breakdown.payableAmount()).isEqualByComparingTo("339.00");
            assertThat(breakdown.durationMinutes()).isEqualTo(290);
            assertThat(breakdown.explanations()).anyMatch(text -> text.contains("冰箱内部清洗"));
        }

        @Test
        @DisplayName("零金额加项不写进计价说明")
        void skipsZeroPriceExplanation() {
            List<PricedOptionValue> selections = List.of(
                    new PricedOptionValue("MATERIAL", "耗材", "SELF", "客户自备",
                            BigDecimal.ZERO, 0, null));

            PriceBreakdown breakdown = PriceCalculator.calculate(fixed(
                    new BigDecimal("199.00"), BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, selections));

            assertThat(breakdown.addonAmount()).isEqualByComparingTo("0.00");
            assertThat(breakdown.explanations()).noneMatch(text -> text.contains("客户自备"));
        }

        @Test
        @DisplayName("负时长增量不会把预计时长压到 0 以下")
        void clampsDurationToOneMinute() {
            List<PricedOptionValue> selections = List.of(
                    new PricedOptionValue("SKIP", "减免", "NO_OVEN", "不清洗烤箱",
                            BigDecimal.ZERO, -500, null));

            PriceBreakdown breakdown = PriceCalculator.calculate(fixed(
                    new BigDecimal("199.00"), BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, selections));

            assertThat(breakdown.durationMinutes()).isEqualTo(1);
        }
    }
}
