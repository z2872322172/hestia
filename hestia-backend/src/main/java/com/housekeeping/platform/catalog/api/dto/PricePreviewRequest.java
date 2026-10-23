package com.housekeeping.platform.catalog.api.dto;

import com.housekeeping.platform.catalog.application.command.PriceQuoteQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格预览请求（docs/modules/04 第 16.4 节）。
 *
 * @param quantity        购买数量，为空按 1 处理
 * @param selectedOptions 已选规格
 */
public record PricePreviewRequest(
        @DecimalMin(value = "0.01", message = "数量必须大于 0") BigDecimal quantity,
        @Valid List<Selection> selectedOptions) {

    /**
     * 转换为应用层查询。
     *
     * @return 查询
     */
    public PriceQuoteQuery toQuery() {
        List<PriceQuoteQuery.OptionSelection> selections = selectedOptions == null
                ? List.of()
                : selectedOptions.stream()
                        .map(selection -> new PriceQuoteQuery.OptionSelection(
                                selection.optionCode(), selection.valueCode()))
                        .toList();
        return new PriceQuoteQuery(quantity, selections);
    }

    /**
     * 已选规格。
     *
     * @param optionCode 规格组编码
     * @param valueCode  规格值编码
     */
    public record Selection(
            @NotBlank(message = "规格编码不能为空") @Size(max = 32, message = "规格编码过长") String optionCode,
            @NotBlank(message = "规格值编码不能为空") @Size(max = 32, message = "规格值编码过长") String valueCode) {
    }
}
