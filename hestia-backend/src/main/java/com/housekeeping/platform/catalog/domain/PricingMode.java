package com.housekeeping.platform.catalog.domain;

/**
 * 计价模式（docs/modules/04 第 6.3 节）。
 *
 * <p>首版只实现 {@link #FIXED} 与 {@link #AREA_TIER}，但结构上把另外两种也留出来：
 * 数据库 CHECK 约束和 {@code PriceCalculator} 的分支都已经支持，
 * 加一种模式不需要改表、不需要改调用方，只需补种子配置。</p>
 */
public enum PricingMode {

    /** 固定价：{@code amount = base_price × quantity}。 */
    FIXED,

    /** 面积阶梯：按面积选档，用档位固定价或面积单价。 */
    AREA_TIER,

    /** 时长阶梯：按小时数选档。 */
    DURATION_TIER,

    /** 数量阶梯：按件数选档。 */
    QUANTITY_TIER;

    /**
     * 该模式是否依赖阶梯配置。
     *
     * @return 非 {@link #FIXED} 均为 {@code true}
     */
    public boolean requiresTiers() {
        return this != FIXED;
    }

    /**
     * 解析字符串，非法值抛 {@link IllegalArgumentException}。
     *
     * @param value 字符串
     * @return 枚举
     */
    public static PricingMode of(String value) {
        for (PricingMode mode : values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知计价模式: " + value);
    }
}
