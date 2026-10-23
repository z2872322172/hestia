package com.housekeeping.platform.catalog.application.view;

import java.math.BigDecimal;

/**
 * 服务项目摘要（列表页）。
 *
 * <p>不含规格与标准：列表页展示这些既没必要，也会让响应体从几 KB 涨到几百 KB。</p>
 *
 * @param itemNo           服务项目编号
 * @param itemName         名称
 * @param shortDescription 一句话描述
 * @param coverUrl         封面
 * @param categoryNo       所属分类编号
 * @param categoryName     所属分类名称
 * @param serviceUnit      销售单位
 * @param pricingMode      计价模式
 * @param basePrice        基础价（阶梯价项目为 0，前端应展示"起"）
 * @param durationMinutes  预计时长
 * @param version          版本号
 * @param status           状态，仅运营接口会返回非 PUBLISHED
 */
public record ServiceItemSummaryView(String itemNo, String itemName, String shortDescription,
                                     String coverUrl, String categoryNo, String categoryName,
                                     String serviceUnit, String pricingMode, BigDecimal basePrice,
                                     int durationMinutes, long version, String status) {
}
