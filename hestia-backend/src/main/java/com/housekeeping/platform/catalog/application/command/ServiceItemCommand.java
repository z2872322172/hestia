package com.housekeeping.platform.catalog.application.command;

import com.housekeeping.platform.catalog.domain.PricingMode;
import com.housekeeping.platform.catalog.domain.SelectionType;
import com.housekeeping.platform.catalog.domain.ServiceUnit;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建/修改服务项目的命令。
 *
 * <p>规格、阶梯、标准一次性提交并<b>整体替换</b>，不做增量 diff。
 * 理由：规格组与阶梯是有内在约束的整体（区间不能重叠、必选项要有值），
 * 增量修改很容易改出一个"暂时不合法"的中间态，而整体替换在事务里一次校验、一次落库，
 * 失败就是全失败，不会留下半套配置。</p>
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
 */
public record ServiceItemCommand(String categoryNo, String itemName, String shortDescription,
                                 String detailDescription, String coverUrl,
                                 ServiceUnit serviceUnit, PricingMode pricingMode,
                                 BigDecimal basePrice, Integer durationMinutes,
                                 BigDecimal minQuantity, BigDecimal maxQuantity,
                                 String serviceScope, String serviceStandardSummary,
                                 List<OptionCommand> options, List<TierCommand> tiers) {

    /**
     * 规格组命令。
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
    public record OptionCommand(String optionCode, String optionName, SelectionType selectionType,
                                boolean required, Integer minSelect, Integer maxSelect,
                                Integer sortOrder, List<OptionValueCommand> values) {
    }

    /**
     * 规格值命令。
     *
     * @param valueCode     编码
     * @param valueName     名称
     * @param numericValue  数值（面积档位用）
     * @param extraPrice    附加金额
     * @param durationDelta 时长增量
     * @param sortOrder     排序
     */
    public record OptionValueCommand(String valueCode, String valueName, BigDecimal numericValue,
                                     BigDecimal extraPrice, Integer durationDelta, Integer sortOrder) {
    }

    /**
     * 价格阶梯命令。
     *
     * @param tierType   阶梯类型
     * @param minValue   下界（含）
     * @param maxValue   上界（不含），{@code null} 表示无上限
     * @param fixedPrice 固定价
     * @param unitPrice  单价
     * @param sortOrder  排序
     */
    public record TierCommand(String tierType, BigDecimal minValue, BigDecimal maxValue,
                              BigDecimal fixedPrice, BigDecimal unitPrice, Integer sortOrder) {
    }
}
