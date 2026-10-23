package com.housekeeping.platform.catalog.domain;

/**
 * 阶梯类型（docs/modules/04 第 12.5 节）。
 *
 * <p>与 {@link PricingMode} 同名但不是同一个概念：计价模式描述"怎么算钱"，
 * 阶梯类型描述"按哪个量分档"。二者目前一一对应，但订单快照要分别记录，
 * 将来出现"按面积分档、再按时长加价"的组合时不需要改表。</p>
 */
public enum TierType {

    /** 按面积分档。 */
    AREA_TIER,

    /** 按时长分档。 */
    DURATION_TIER,

    /** 按数量分档。 */
    QUANTITY_TIER;

    /**
     * 由计价模式推导阶梯类型。
     *
     * @param mode 计价模式
     * @return 阶梯类型；{@link PricingMode#FIXED} 没有阶梯，返回 {@code null}
     */
    public static TierType of(PricingMode mode) {
        return switch (mode) {
            case FIXED -> null;
            case AREA_TIER -> AREA_TIER;
            case DURATION_TIER -> DURATION_TIER;
            case QUANTITY_TIER -> QUANTITY_TIER;
        };
    }
}
