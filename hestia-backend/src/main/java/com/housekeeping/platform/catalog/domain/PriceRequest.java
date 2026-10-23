package com.housekeeping.platform.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计价入参（已解析，不含任何数据库访问）。
 *
 * @param pricingMode     计价模式
 * @param basePrice       服务项目基础价，{@link PricingMode#FIXED} 时参与计算
 * @param durationMinutes 服务项目基础时长（分钟）
 * @param minQuantity     数量下限
 * @param maxQuantity     数量上限，{@code null} 表示不限
 * @param quantity        本次购买数量
 * @param measure         用于选档的度量值（面积 / 小时 / 件数），
 *                        {@link PricingMode#FIXED} 时可为 {@code null}
 * @param selections      已校验通过的选项
 * @param tiers           阶梯配置，{@link PricingMode#FIXED} 时为空
 */
public record PriceRequest(PricingMode pricingMode,
                           BigDecimal basePrice,
                           int durationMinutes,
                           BigDecimal minQuantity,
                           BigDecimal maxQuantity,
                           BigDecimal quantity,
                           BigDecimal measure,
                           List<PricedOptionValue> selections,
                           List<PricedTier> tiers) {

    /**
     * 构造只含基础价、没有任何加项的请求，便于测试与内部调用。
     *
     * @param pricingMode     计价模式
     * @param basePrice       基础价
     * @param durationMinutes 基础时长
     * @param quantity        数量
     * @return 计价入参
     */
    public static PriceRequest fixed(PricingMode pricingMode, BigDecimal basePrice,
                                     int durationMinutes, BigDecimal quantity) {
        return new PriceRequest(pricingMode, basePrice, durationMinutes,
                BigDecimal.ONE, BigDecimal.ONE, quantity, null, List.of(), List.of());
    }
}
