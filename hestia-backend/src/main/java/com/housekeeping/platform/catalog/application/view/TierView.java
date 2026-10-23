package com.housekeeping.platform.catalog.application.view;

import java.math.BigDecimal;

/**
 * 价格阶梯视图。
 *
 * @param tierType   阶梯类型
 * @param minValue   下界（含）
 * @param maxValue   上界（不含），{@code null} 表示无上限
 * @param fixedPrice 档位固定价
 * @param unitPrice  档位单价
 */
public record TierView(String tierType, BigDecimal minValue, BigDecimal maxValue,
                       BigDecimal fixedPrice, BigDecimal unitPrice) {
}
