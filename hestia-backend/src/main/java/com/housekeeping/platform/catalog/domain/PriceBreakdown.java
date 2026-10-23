package com.housekeeping.platform.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计价结果（docs/modules/04 第 8.6 节）。
 *
 * <p>金额字段全部保留两位小数、按 {@code HALF_UP} 舍入，与数据库 {@code DECIMAL(12,2)} 对齐。
 * 不在这里做"看起来更整"的取整——少收或多收一分钱，对账时都要查半天。</p>
 *
 * @param baseAmount      基础服务费
 * @param addonAmount     加项费用
 * @param materialAmount  耗材费用（首版恒为 0，字段先留）
 * @param travelFee       上门费（首版恒为 0，字段先留）
 * @param discountAmount  优惠金额（首版恒为 0）
 * @param payableAmount   应付金额
 * @param durationMinutes 预计服务时长（分钟）
 * @param explanations    计价说明，按"基础 → 加项 → 合计"顺序
 */
public record PriceBreakdown(BigDecimal baseAmount,
                             BigDecimal addonAmount,
                             BigDecimal materialAmount,
                             BigDecimal travelFee,
                             BigDecimal discountAmount,
                             BigDecimal payableAmount,
                             int durationMinutes,
                             List<String> explanations) {
}
