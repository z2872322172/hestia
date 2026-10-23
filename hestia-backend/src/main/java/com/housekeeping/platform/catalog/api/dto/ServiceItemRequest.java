package com.housekeeping.platform.catalog.api.dto;

import com.housekeeping.platform.catalog.application.command.ServiceItemCommand;
import com.housekeeping.platform.catalog.domain.PricingMode;
import com.housekeeping.platform.catalog.domain.SelectionType;
import com.housekeeping.platform.catalog.domain.ServiceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建/修改服务项目请求。
 *
 * <p>没有金额类的"客户端报价"字段：金额只能由服务端算（docs/modules/04 第 8.7 节）。</p>
 *
 * @param categoryNo             所属分类编号
 * @param itemName               名称
 * @param shortDescription       一句话描述
 * @param detailDescription      详情
 * @param coverUrl               封面
 * @param serviceUnit            销售单位
 * @param pricingMode            计价模式
 * @param basePrice              基础价
 * @param durationMinutes        预计时长
 * @param minQuantity            数量下限
 * @param maxQuantity            数量上限
 * @param serviceScope           服务范围说明
 * @param serviceStandardSummary 标准摘要
 * @param options                规格组
 * @param tiers                  价格阶梯
 * @param version                修改时必填，取自详情接口返回的版本号
 */
public record ServiceItemRequest(
        @NotBlank(message = "分类编号不能为空") @Size(max = 32, message = "分类编号过长") String categoryNo,
        @NotBlank(message = "服务名称不能为空") @Size(max = 128, message = "服务名称过长") String itemName,
        @Size(max = 255, message = "简短描述过长") String shortDescription,
        String detailDescription,
        @Size(max = 512, message = "封面地址过长") String coverUrl,
        @NotNull(message = "销售单位不能为空") ServiceUnit serviceUnit,
        @NotNull(message = "计价模式不能为空") PricingMode pricingMode,
        @NotNull(message = "基础价不能为空") @DecimalMin(value = "0.00", message = "基础价不能为负")
        BigDecimal basePrice,
        @NotNull(message = "服务时长不能为空") @Min(value = 1, message = "服务时长必须大于 0")
        Integer durationMinutes,
        @DecimalMin(value = "0.01", message = "数量下限必须大于 0") BigDecimal minQuantity,
        BigDecimal maxQuantity,
        String serviceScope,
        String serviceStandardSummary,
        @Valid List<OptionRequest> options,
        @Valid List<TierRequest> tiers,
        Long version) {

    /**
     * 转换为应用层命令。
     *
     * @return 命令
     */
    public ServiceItemCommand toCommand() {
        List<ServiceItemCommand.OptionCommand> optionCommands = options == null
                ? List.of()
                : options.stream().map(OptionRequest::toCommand).toList();
        List<ServiceItemCommand.TierCommand> tierCommands = tiers == null
                ? List.of()
                : tiers.stream().map(TierRequest::toCommand).toList();
        return new ServiceItemCommand(categoryNo, itemName, shortDescription, detailDescription,
                coverUrl, serviceUnit, pricingMode, basePrice, durationMinutes, minQuantity,
                maxQuantity, serviceScope, serviceStandardSummary, optionCommands, tierCommands);
    }

    /**
     * 规格组请求。
     *
     * @param optionCode    编码
     * @param optionName    名称
     * @param selectionType 单选/多选
     * @param required      是否必选
     * @param minSelect     最少选择数
     * @param maxSelect     最多选择数
     * @param sortOrder     排序
     * @param values        规格值
     */
    public record OptionRequest(
            @NotBlank(message = "规格编码不能为空") @Size(max = 32, message = "规格编码过长") String optionCode,
            @NotBlank(message = "规格名称不能为空") @Size(max = 64, message = "规格名称过长") String optionName,
            @NotNull(message = "选择方式不能为空") SelectionType selectionType,
            boolean required,
            Integer minSelect,
            Integer maxSelect,
            Integer sortOrder,
            @NotEmpty(message = "规格至少要有一个可选值") @Valid List<OptionValueRequest> values) {

        ServiceItemCommand.OptionCommand toCommand() {
            List<ServiceItemCommand.OptionValueCommand> valueCommands = values.stream()
                    .map(OptionValueRequest::toCommand).toList();
            return new ServiceItemCommand.OptionCommand(optionCode, optionName, selectionType,
                    required, minSelect, maxSelect, sortOrder, valueCommands);
        }
    }

    /**
     * 规格值请求。
     *
     * @param valueCode     编码
     * @param valueName     名称
     * @param numericValue  数值（面积档位用）
     * @param extraPrice    附加金额
     * @param durationDelta 时长增量
     * @param sortOrder     排序
     */
    public record OptionValueRequest(
            @NotBlank(message = "规格值编码不能为空") @Size(max = 32, message = "规格值编码过长") String valueCode,
            @NotBlank(message = "规格值名称不能为空") @Size(max = 64, message = "规格值名称过长") String valueName,
            BigDecimal numericValue,
            @DecimalMin(value = "0.00", message = "附加金额不能为负") BigDecimal extraPrice,
            Integer durationDelta,
            Integer sortOrder) {

        ServiceItemCommand.OptionValueCommand toCommand() {
            return new ServiceItemCommand.OptionValueCommand(valueCode, valueName, numericValue,
                    extraPrice, durationDelta, sortOrder);
        }
    }

    /**
     * 价格阶梯请求。
     *
     * @param tierType   阶梯类型
     * @param minValue   下界（含）
     * @param maxValue   上界（不含），为空表示无上限
     * @param fixedPrice 固定价
     * @param unitPrice  单价
     * @param sortOrder  排序
     */
    public record TierRequest(
            @NotBlank(message = "阶梯类型不能为空") @Size(max = 24, message = "阶梯类型过长") String tierType,
            @NotNull(message = "阶梯下界不能为空")
            @DecimalMin(value = "0.00", message = "阶梯下界不能为负") BigDecimal minValue,
            BigDecimal maxValue,
            @DecimalMin(value = "0.00", message = "固定价不能为负") BigDecimal fixedPrice,
            @DecimalMin(value = "0.00", message = "单价不能为负") BigDecimal unitPrice,
            Integer sortOrder) {

        ServiceItemCommand.TierCommand toCommand() {
            return new ServiceItemCommand.TierCommand(tierType, minValue, maxValue,
                    fixedPrice, unitPrice, sortOrder);
        }
    }
}
