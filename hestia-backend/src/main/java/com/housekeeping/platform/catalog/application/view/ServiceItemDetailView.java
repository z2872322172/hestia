package com.housekeeping.platform.catalog.application.view;

import java.math.BigDecimal;
import java.util.List;

/**
 * 服务项目详情。
 *
 * @param itemNo                 服务项目编号
 * @param itemName               名称
 * @param shortDescription       一句话描述
 * @param detailDescription      详情（富文本/纯文本）
 * @param coverUrl               封面
 * @param categoryNo             所属分类编号
 * @param categoryName           所属分类名称
 * @param serviceUnit            销售单位
 * @param pricingMode            计价模式
 * @param basePrice              基础价
 * @param durationMinutes        预计时长
 * @param minQuantity            数量下限
 * @param maxQuantity            数量上限，{@code null} 表示不限
 * @param serviceScope           服务范围说明
 * @param serviceStandardSummary 标准摘要
 * @param version                版本号
 * @param status                 状态
 * @param options                规格组
 * @param tiers                  价格阶梯
 * @param standards              标准检查项
 */
public record ServiceItemDetailView(String itemNo, String itemName, String shortDescription,
                                    String detailDescription, String coverUrl,
                                    String categoryNo, String categoryName,
                                    String serviceUnit, String pricingMode, BigDecimal basePrice,
                                    int durationMinutes, BigDecimal minQuantity, BigDecimal maxQuantity,
                                    String serviceScope, String serviceStandardSummary,
                                    long version, String status,
                                    List<OptionView> options, List<TierView> tiers,
                                    List<StandardItemView> standards) {
}
