package com.housekeeping.platform.catalog.application.view;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格预览结果（docs/modules/04 第 16.5 节）。
 *
 * <p>带 {@code serviceItemVersion}：前端下单时把它原样回传，
 * 服务端比对版本不一致就拒绝——避免用户看着 299 的页面、付了 399 的单。</p>
 *
 * @param serviceItemVersion  服务项目版本
 * @param baseAmount          基础服务费
 * @param addonAmount         加项费用
 * @param materialAmount      耗材费用
 * @param travelFee           上门费
 * @param discountAmount      优惠金额
 * @param payableAmount       应付金额
 * @param durationMinutes     预计服务时长
 * @param pricingExplanation  计价说明
 */
public record PricePreviewView(long serviceItemVersion,
                               BigDecimal baseAmount, BigDecimal addonAmount,
                               BigDecimal materialAmount, BigDecimal travelFee,
                               BigDecimal discountAmount, BigDecimal payableAmount,
                               int durationMinutes, List<String> pricingExplanation) {
}
