package com.housekeeping.platform.catalog.application.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格预览查询。
 *
 * <p>不含任何金额字段：客户端传进来的金额一律忽略（docs/modules/04 第 8.7 节）。
 * 结构上就不给它留位置，比"接收了但不用"更安全——后者总有人哪天顺手用上。</p>
 *
 * @param quantity        购买数量
 * @param selectedOptions 已选规格
 */
public record PriceQuoteQuery(BigDecimal quantity, List<OptionSelection> selectedOptions) {

    /**
     * 已选规格。
     *
     * @param optionCode 规格组编码
     * @param valueCode  规格值编码
     */
    public record OptionSelection(String optionCode, String valueCode) {
    }
}
