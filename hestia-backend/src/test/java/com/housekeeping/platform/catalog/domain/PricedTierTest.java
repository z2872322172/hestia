package com.housekeeping.platform.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 价格档位测试：区间语义与重叠判断。
 */
class PricedTierTest {

    private static PricedTier tier(String min, String max) {
        return new PricedTier(new BigDecimal(min), max == null ? null : new BigDecimal(max),
                new BigDecimal("399.00"), null);
    }

    @Test
    @DisplayName("左闭右开：下界命中、上界不命中")
    void usesHalfOpenInterval() {
        PricedTier tier = tier("60", "90");

        assertThat(tier.contains(new BigDecimal("60"))).isTrue();
        assertThat(tier.contains(new BigDecimal("89.99"))).isTrue();
        assertThat(tier.contains(new BigDecimal("90"))).isFalse();
        assertThat(tier.contains(new BigDecimal("59.99"))).isFalse();
    }

    @Test
    @DisplayName("上界为空表示不封顶")
    void openEndedTierAcceptsAnyUpperValue() {
        PricedTier tier = tier("120", null);

        assertThat(tier.contains(new BigDecimal("120"))).isTrue();
        assertThat(tier.contains(new BigDecimal("100000"))).isTrue();
        assertThat(tier.contains(new BigDecimal("119.99"))).isFalse();
    }

    @Test
    @DisplayName("相邻档位不重叠")
    void adjacentTiersDoNotOverlap() {
        assertThat(tier("0", "60").overlaps(tier("60", "90"))).isFalse();
        assertThat(tier("60", "90").overlaps(tier("0", "60"))).isFalse();
    }

    @Test
    @DisplayName("交叉或包含都算重叠")
    void detectsOverlap() {
        assertThat(tier("0", "60").overlaps(tier("50", "90"))).isTrue();
        assertThat(tier("0", "120").overlaps(tier("60", "90"))).isTrue();
    }

    @Test
    @DisplayName("一端无上限时与任何更高区间重叠")
    void openEndedTierOverlapsHigherTier() {
        assertThat(tier("120", null).overlaps(tier("200", "300"))).isTrue();
    }

    @Test
    @DisplayName("固定价与单价二选一的判断")
    void reportsPriceKind() {
        assertThat(tier("0", "60").usesFixedPrice()).isTrue();
        assertThat(new PricedTier(BigDecimal.ZERO, null, null, new BigDecimal("6.00"))
                .usesFixedPrice()).isFalse();
    }
}
