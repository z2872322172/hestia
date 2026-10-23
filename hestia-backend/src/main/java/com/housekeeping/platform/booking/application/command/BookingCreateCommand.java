package com.housekeeping.platform.booking.application.command;

import com.housekeeping.platform.catalog.application.command.PriceQuoteQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建订单命令（docs/modules/07 第 18.5 节）。
 *
 * @param serviceItemNo     服务项目编号
 * @param appointmentDate   服务日期
 * @param serviceWindowCode 服务时间窗编码，如 MORNING
 * @param addressNo         服务地址编号（客户本人地址）
 * @param quantity          购买数量
 * @param options           已选规格（规格组编码 + 规格值编码）
 */
public record BookingCreateCommand(String serviceItemNo,
                                   LocalDate appointmentDate,
                                   String serviceWindowCode,
                                   String addressNo,
                                   BigDecimal quantity,
                                   List<PriceQuoteQuery.OptionSelection> options) {

    public BookingCreateCommand {
        if (options == null) {
            options = List.of();
        }
    }
}
