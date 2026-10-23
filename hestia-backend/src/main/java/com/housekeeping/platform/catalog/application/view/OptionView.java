package com.housekeeping.platform.catalog.application.view;

import java.math.BigDecimal;
import java.util.List;

/**
 * 服务规格组视图。
 *
 * @param optionCode    规格组编码
 * @param optionName    规格组名称
 * @param selectionType SINGLE / MULTIPLE
 * @param required      是否必选
 * @param minSelect     最少选择数
 * @param maxSelect     最多选择数
 * @param values        可选值
 */
public record OptionView(String optionCode, String optionName, String selectionType,
                         boolean required, int minSelect, int maxSelect,
                         List<OptionValueView> values) {

    /**
     * 规格值视图。
     *
     * @param valueCode     值编码
     * @param valueName     值名称
     * @param numericValue  数值（面积档位用）
     * @param extraPrice    附加金额
     * @param durationDelta 时长增量（分钟）
     */
    public record OptionValueView(String valueCode, String valueName, BigDecimal numericValue,
                                  BigDecimal extraPrice, int durationDelta) {
    }
}
