package com.housekeeping.platform.booking.api.dto;

import com.housekeeping.platform.booking.application.command.BookingCreateCommand;
import com.housekeeping.platform.catalog.application.command.PriceQuoteQuery;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建/预览订单请求（docs/modules/07 第 18.5 节）。
 *
 * <p>金额不进请求：价格一律以服务端重算为准，前端展示价只做参考
 * （docs/modules/04 第 12 节防篡改口径）。</p>
 *
 * @param serviceItemNo     服务项目编号
 * @param appointmentDate   服务日期（ISO：yyyy-MM-dd）
 * @param serviceWindowCode 服务时间窗编码，如 MORNING
 * @param addressNo         服务地址编号
 * @param quantity          购买数量
 * @param options           已选规格
 */
public record BookingCreateRequest(
        @NotBlank String serviceItemNo,
        @NotNull LocalDate appointmentDate,
        @NotBlank String serviceWindowCode,
        @NotBlank String addressNo,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "9999") BigDecimal quantity,
        @Size(max = 20) List<PriceQuoteQuery.OptionSelection> options) {

    public BookingCreateRequest {
        if (options == null) {
            options = List.of();
        }
    }

    /**
     * 转下单命令。
     *
     * @return 下单命令
     */
    public BookingCreateCommand toCommand() {
        return new BookingCreateCommand(serviceItemNo, appointmentDate,
                serviceWindowCode, addressNo, quantity, options);
    }
}
