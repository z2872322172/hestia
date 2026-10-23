package com.housekeeping.platform.catalog.application;

import com.housekeeping.platform.catalog.application.command.PriceQuoteQuery;
import com.housekeeping.platform.catalog.application.view.PricePreviewView;
import com.housekeeping.platform.catalog.domain.PriceBreakdown;
import com.housekeeping.platform.catalog.domain.PriceCalculator;
import com.housekeeping.platform.catalog.domain.PriceRequest;
import com.housekeeping.platform.catalog.domain.PricedOptionValue;
import com.housekeeping.platform.catalog.domain.PricedTier;
import com.housekeeping.platform.catalog.domain.PricingMode;
import com.housekeeping.platform.catalog.domain.TierType;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionValueEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServicePriceTierEntity;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionValueMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServicePriceTierMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 价格预览（跨模块公开契约，docs/07 第 11.4 节 {@code PriceQuoteService}）。
 *
 * <p>三件事按顺序做，缺一不可：</p>
 * <ol>
 *   <li><b>校验规格</b>：必选项有没有选、单选有没有多选、多选有没有超上限、
 *       选项是不是属于这个服务项目；</li>
 *   <li><b>解析度量值</b>：阶梯价需要一个数（面积/小时/件数），从规格值的
 *       {@code numericValue} 取；</li>
 *   <li><b>交给纯函数算钱</b>：{@link PriceCalculator} 不查库，只做算术。</li>
 * </ol>
 *
 * <p>只读：不写库、不加锁。下单时必须在订单事务里再调一次本方法重算，
 * 客户端传回来的金额一律不采信（docs/modules/04 第 8.7 节）。</p>
 */
@Service
@RequiresPersistence
public class PriceQuoteService {

    private static final Logger LOG = LoggerFactory.getLogger(PriceQuoteService.class);

    private final ServiceItemMapper itemMapper;

    private final ServiceItemOptionMapper optionMapper;

    private final ServiceItemOptionValueMapper optionValueMapper;

    private final ServicePriceTierMapper tierMapper;

    public PriceQuoteService(ServiceItemMapper itemMapper,
                             ServiceItemOptionMapper optionMapper,
                             ServiceItemOptionValueMapper optionValueMapper,
                             ServicePriceTierMapper tierMapper) {
        this.itemMapper = itemMapper;
        this.optionMapper = optionMapper;
        this.optionValueMapper = optionValueMapper;
        this.tierMapper = tierMapper;
    }

    /**
     * 计算价格预览。
     *
     * @param itemNo 服务项目编号
     * @param query  数量与已选规格
     * @return 价格明细
     * @throws BusinessException 服务项目不可售、规格不合法、阶梯缺失
     */
    @Transactional(readOnly = true)
    public PricePreviewView preview(String itemNo, PriceQuoteQuery query) {
        ServiceItemEntity item = itemMapper.selectPublishedByItemNo(itemNo);
        if (item == null) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND);
        }

        PricingMode mode = PricingMode.of(item.getPricingMode());
        List<ServiceItemOptionEntity> options = optionMapper.selectActiveByItemId(item.getId());
        Map<Long, List<ServiceItemOptionValueEntity>> valuesByOption = loadValues(options);

        Map<String, List<String>> selectedByOption = groupSelections(query);
        assertNoUnknownOption(options, selectedByOption);

        List<PricedOptionValue> pricedSelections = new ArrayList<>();
        BigDecimal measure = null;
        for (ServiceItemOptionEntity option : options) {
            List<String> selectedValues = selectedByOption.getOrDefault(option.getOptionCode(), List.of());
            assertSelectionCount(option, selectedValues.size());
            for (String valueCode : selectedValues) {
                ServiceItemOptionValueEntity value = findValue(valuesByOption, option.getId(), valueCode);
                if (value == null) {
                    throw new BusinessException(ErrorCode.SERVICE_OPTION_INVALID);
                }
                pricedSelections.add(new PricedOptionValue(option.getOptionCode(), option.getOptionName(),
                        value.getValueCode(), value.getValueName(), value.getExtraPrice(),
                        value.getDurationDelta() == null ? 0 : value.getDurationDelta(),
                        value.getNumericValue()));
                if (value.getNumericValue() != null) {
                    measure = measure == null ? value.getNumericValue()
                            : measure.max(value.getNumericValue());
                }
            }
        }

        if (mode.requiresTiers() && measure == null) {
            // 阶梯价项目必须有一个带数值的规格来选档，否则算不出价格。
            // 报"请选择必填规格"而不是"没有阶梯"——问题出在入参，不是配置。
            throw new BusinessException(ErrorCode.SERVICE_OPTION_REQUIRED);
        }

        List<PricedTier> tiers = mode.requiresTiers()
                ? loadTiers(item.getId(), TierType.of(mode))
                : List.of();

        PriceRequest request = new PriceRequest(mode, item.getBasePrice(),
                item.getDurationMinutes() == null ? 0 : item.getDurationMinutes(),
                item.getMinQuantity(), item.getMaxQuantity(),
                query.quantity(), measure, pricedSelections, tiers);

        PriceBreakdown breakdown = PriceCalculator.calculate(request);
        LOG.debug("价格预览 itemNo={} version={} payable={}", itemNo, item.getVersion(),
                breakdown.payableAmount());

        return new PricePreviewView(item.getVersion(), breakdown.baseAmount(),
                breakdown.addonAmount(), breakdown.materialAmount(), breakdown.travelFee(),
                breakdown.discountAmount(), breakdown.payableAmount(),
                breakdown.durationMinutes(), breakdown.explanations());
    }

    private Map<Long, List<ServiceItemOptionValueEntity>> loadValues(List<ServiceItemOptionEntity> options) {
        if (options.isEmpty()) {
            return Map.of();
        }
        List<Long> optionIds = options.stream().map(ServiceItemOptionEntity::getId).toList();
        Map<Long, List<ServiceItemOptionValueEntity>> byOption = new HashMap<>();
        for (ServiceItemOptionValueEntity value : optionValueMapper.selectActiveByOptionIds(optionIds)) {
            byOption.computeIfAbsent(value.getOptionId(), key -> new ArrayList<>()).add(value);
        }
        return byOption;
    }

    private Map<String, List<String>> groupSelections(PriceQuoteQuery query) {
        Map<String, List<String>> grouped = new HashMap<>();
        if (query == null || query.selectedOptions() == null) {
            return grouped;
        }
        for (PriceQuoteQuery.OptionSelection selection : query.selectedOptions()) {
            if (selection == null || selection.optionCode() == null || selection.optionCode().isBlank()
                    || selection.valueCode() == null || selection.valueCode().isBlank()) {
                throw new BusinessException(ErrorCode.SERVICE_OPTION_INVALID);
            }
            List<String> values = grouped.computeIfAbsent(selection.optionCode(),
                    key -> new ArrayList<>());
            if (values.contains(selection.valueCode())) {
                // 同一个值重复提交：前端 bug 或用户重复点击，直接拒绝比"去重后照算"更早暴露问题
                throw new BusinessException(ErrorCode.SERVICE_OPTION_INVALID);
            }
            values.add(selection.valueCode());
        }
        return grouped;
    }

    private void assertNoUnknownOption(List<ServiceItemOptionEntity> options,
                                       Map<String, List<String>> selectedByOption) {
        Set<String> known = new HashSet<>();
        for (ServiceItemOptionEntity option : options) {
            known.add(option.getOptionCode());
        }
        for (String optionCode : selectedByOption.keySet()) {
            if (!known.contains(optionCode)) {
                // 停用或不属于本项目的规格组：对调用方而言就是"无效选项"
                throw new BusinessException(ErrorCode.SERVICE_OPTION_INVALID);
            }
        }
    }

    private void assertSelectionCount(ServiceItemOptionEntity option, int selectedCount) {
        int minSelect = option.getMinSelect() == null ? 0 : option.getMinSelect();
        int maxSelect = option.getMaxSelect() == null ? 1 : option.getMaxSelect();
        boolean required = Boolean.TRUE.equals(option.getRequired());

        if ("SINGLE".equals(option.getSelectionType()) && selectedCount > 1) {
            throw new BusinessException(ErrorCode.SERVICE_OPTION_SELECTION_LIMIT);
        }
        if (selectedCount > maxSelect) {
            throw new BusinessException(ErrorCode.SERVICE_OPTION_SELECTION_LIMIT);
        }
        // 必选至少选 1 个，即使配置里 min_select 写的是 0
        int requiredMin = required ? Math.max(minSelect, 1) : minSelect;
        if (selectedCount < requiredMin) {
            throw new BusinessException(ErrorCode.SERVICE_OPTION_REQUIRED);
        }
    }

    private ServiceItemOptionValueEntity findValue(Map<Long, List<ServiceItemOptionValueEntity>> valuesByOption,
                                                   Long optionId, String valueCode) {
        for (ServiceItemOptionValueEntity value : valuesByOption.getOrDefault(optionId, List.of())) {
            if (value.getValueCode().equals(valueCode)) {
                return value;
            }
        }
        return null;
    }

    private List<PricedTier> loadTiers(long serviceItemId, TierType tierType) {
        List<ServicePriceTierEntity> tiers = tierMapper.selectActiveByItemAndType(serviceItemId, tierType.name());
        List<PricedTier> result = new ArrayList<>(tiers.size());
        for (ServicePriceTierEntity tier : tiers) {
            result.add(new PricedTier(tier.getMinValue(), tier.getMaxValue(),
                    tier.getFixedPrice(), tier.getUnitPrice()));
        }
        return result;
    }
}
