package com.housekeeping.platform.catalog.domain;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端计价器（docs/modules/04 第 8 节）。
 *
 * <p>为什么是纯函数而不是 {@code @Service}：</p>
 * <ul>
 *   <li>计价是金额的唯一事实来源，必须能被穷举测试——边界档位、加项累加、四舍五入，
 *       每一条都要有单测。纯函数不需要 Spring、不需要数据库，测起来才有人愿意写。</li>
 *   <li>下单时要在<b>订单事务内</b>重算一次，如果计价器自己查库，
 *       就会在事务里再开一批读，锁范围失控。</li>
 * </ul>
 *
 * <p>金额一律 {@link RoundingMode#HALF_UP} 保留两位，与 {@code DECIMAL(12,2)} 对齐。</p>
 */
public final class PriceCalculator {

    private static final int AMOUNT_SCALE = 2;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(AMOUNT_SCALE, ROUNDING);

    private PriceCalculator() {
    }

    /**
     * 计算价格。
     *
     * @param request 计价入参
     * @return 价格明细
     * @throws BusinessException 数量越界、阶梯缺失或阶梯重叠
     */
    public static PriceBreakdown calculate(PriceRequest request) {
        BigDecimal quantity = request.quantity() == null ? BigDecimal.ONE : request.quantity();
        assertQuantityInRange(request, quantity);

        List<String> explanations = new ArrayList<>();
        BigDecimal baseAmount = switch (request.pricingMode()) {
            case FIXED -> fixedBase(request, quantity, explanations);
            case AREA_TIER, DURATION_TIER, QUANTITY_TIER -> tieredBase(request, quantity, explanations);
        };

        BigDecimal addonAmount = ZERO;
        int durationMinutes = request.durationMinutes();
        List<PricedOptionValue> selections = request.selections() == null
                ? List.of() : request.selections();
        for (PricedOptionValue selection : selections) {
            BigDecimal extraPrice = scale(selection.extraPrice());
            if (extraPrice.signum() != 0) {
                addonAmount = addonAmount.add(extraPrice);
                explanations.add(selection.optionName() + " · " + selection.valueName()
                        + "：" + plain(extraPrice) + " 元");
            }
            durationMinutes += selection.durationDelta();
        }
        addonAmount = scale(addonAmount);

        BigDecimal materialAmount = ZERO;
        BigDecimal travelFee = ZERO;
        BigDecimal discountAmount = ZERO;
        BigDecimal payableAmount = scale(baseAmount.add(addonAmount)
                .add(materialAmount).add(travelFee).subtract(discountAmount));

        explanations.add("应付合计：" + plain(payableAmount) + " 元");

        // 时长增量可能是负数（例如"不需要拆洗"省时间），但预计时长不能小于 1 分钟，
        // 否则排班会拿到一个 0 分钟的服务时长，生成不出任何可用时段。
        int safeDuration = Math.max(durationMinutes, 1);

        return new PriceBreakdown(baseAmount, addonAmount, materialAmount, travelFee,
                discountAmount, payableAmount, safeDuration, List.copyOf(explanations));
    }

    /**
     * 校验同一阶梯类型下的档位区间不重叠（发布服务项目时调用）。
     *
     * @param tiers 档位列表
     * @throws BusinessException 存在重叠
     */
    public static void assertNoOverlap(List<PricedTier> tiers) {
        if (tiers == null || tiers.size() < 2) {
            return;
        }
        for (int i = 0; i < tiers.size(); i++) {
            for (int j = i + 1; j < tiers.size(); j++) {
                if (tiers.get(i).overlaps(tiers.get(j))) {
                    throw new BusinessException(ErrorCode.SERVICE_PRICE_TIER_CONFLICT);
                }
            }
        }
    }

    private static BigDecimal fixedBase(PriceRequest request, BigDecimal quantity,
                                        List<String> explanations) {
        BigDecimal basePrice = scale(request.basePrice());
        BigDecimal amount = scale(basePrice.multiply(quantity));
        explanations.add("基础服务：" + plain(basePrice) + " 元 × " + plain(quantity)
                + " = " + plain(amount) + " 元");
        return amount;
    }

    private static BigDecimal tieredBase(PriceRequest request, BigDecimal quantity,
                                         List<String> explanations) {
        BigDecimal measure = request.measure() != null ? request.measure() : quantity;
        PricedTier tier = findTier(request.tiers(), measure);
        String subject = describeMeasure(request.pricingMode(), measure);

        if (tier.usesFixedPrice()) {
            BigDecimal amount = scale(tier.fixedPrice());
            explanations.add(subject + "，命中档位 " + describeRange(tier)
                    + "：固定价 " + plain(amount) + " 元");
            return amount;
        }

        BigDecimal unitPrice = scale(tier.unitPrice());
        // 数量阶梯用购买数量做乘数；面积与时长阶梯用度量值本身。
        BigDecimal multiplier = request.pricingMode() == PricingMode.QUANTITY_TIER ? quantity : measure;
        BigDecimal amount = scale(unitPrice.multiply(multiplier));
        explanations.add(subject + "，命中档位 " + describeRange(tier) + "："
                + plain(unitPrice) + " 元 × " + plain(multiplier) + " = " + plain(amount) + " 元");
        return amount;
    }

    /**
     * 找到唯一命中的档位。
     *
     * <p>命中多个档位说明配置有重叠——这时不能"取第一个"糊过去，
     * 因为运营看到的报价会随档位顺序变化，属于最难排查的那类线上问题。
     * 直接报冲突，把配置错误暴露在报价环节。</p>
     */
    private static PricedTier findTier(List<PricedTier> tiers, BigDecimal measure) {
        if (tiers == null || tiers.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_PRICE_TIER_NOT_FOUND);
        }
        PricedTier matched = null;
        for (PricedTier tier : tiers) {
            if (tier.contains(measure)) {
                if (matched != null) {
                    throw new BusinessException(ErrorCode.SERVICE_PRICE_TIER_CONFLICT);
                }
                matched = tier;
            }
        }
        if (matched == null) {
            throw new BusinessException(ErrorCode.SERVICE_PRICE_TIER_NOT_FOUND);
        }
        return matched;
    }

    private static void assertQuantityInRange(PriceRequest request, BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.SERVICE_QUANTITY_OUT_OF_RANGE);
        }
        BigDecimal min = request.minQuantity() == null ? BigDecimal.ONE : request.minQuantity();
        if (quantity.compareTo(min) < 0) {
            throw new BusinessException(ErrorCode.SERVICE_QUANTITY_OUT_OF_RANGE);
        }
        if (request.maxQuantity() != null && quantity.compareTo(request.maxQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SERVICE_QUANTITY_OUT_OF_RANGE);
        }
    }

    private static String describeMeasure(PricingMode mode, BigDecimal measure) {
        return switch (mode) {
            case AREA_TIER -> "面积 " + plain(measure) + " 平米";
            case DURATION_TIER -> "时长 " + plain(measure) + " 小时";
            case QUANTITY_TIER -> "数量 " + plain(measure) + " 件";
            case FIXED -> "固定价";
        };
    }

    private static String describeRange(PricedTier tier) {
        String upper = tier.maxValue() == null ? "无上限" : plain(tier.maxValue());
        return "[" + plain(tier.minValue()) + ", " + upper + ")";
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(AMOUNT_SCALE, ROUNDING);
    }

    private static String plain(BigDecimal value) {
        return scale(value).toPlainString();
    }
}
