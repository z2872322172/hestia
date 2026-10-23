package com.housekeeping.platform.catalog.domain;

import java.math.BigDecimal;

/**
 * 一个价格阶梯档位。
 *
 * <p>区间语义是左闭右开 {@code [minValue, maxValue)}，{@code maxValue} 为 {@code null}
 * 表示"上不封顶"。用左闭右开而不是闭区间，是为了让 60 平米只落在一个档里——
 * 闭区间会让相邻两档在边界上重叠，正好和"区间不得重叠"的约束冲突。</p>
 *
 * @param minValue   下界（含）
 * @param maxValue   上界（不含），{@code null} 表示无上限
 * @param fixedPrice 档位固定价，与 {@code unitPrice} 二选一
 * @param unitPrice  档位单价，与 {@code fixedPrice} 二选一
 */
public record PricedTier(BigDecimal minValue, BigDecimal maxValue,
                         BigDecimal fixedPrice, BigDecimal unitPrice) {

    /**
     * 判断给定值是否落在本档。
     *
     * @param value 面积 / 时长 / 数量
     * @return 是否命中
     */
    public boolean contains(BigDecimal value) {
        if (value == null || value.compareTo(minValue) < 0) {
            return false;
        }
        return maxValue == null || value.compareTo(maxValue) < 0;
    }

    /**
     * 是否使用固定价。
     *
     * @return 固定价非空返回 {@code true}
     */
    public boolean usesFixedPrice() {
        return fixedPrice != null;
    }

    /**
     * 本档是否与另一档区间重叠。
     *
     * <p>用"两个区间不重叠"的充要条件判断：{@code a.max <= b.min || b.max <= a.min}。
     * 只要一端无上限就视为与该侧重叠。</p>
     *
     * @param other 另一档
     * @return 是否重叠
     */
    public boolean overlaps(PricedTier other) {
        boolean thisEndsBeforeOther = maxValue != null && maxValue.compareTo(other.minValue) <= 0;
        boolean otherEndsBeforeThis = other.maxValue != null && other.maxValue.compareTo(minValue) <= 0;
        return !thisEndsBeforeOther && !otherEndsBeforeThis;
    }
}
