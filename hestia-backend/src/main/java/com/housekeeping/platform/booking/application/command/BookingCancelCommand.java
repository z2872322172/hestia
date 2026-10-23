package com.housekeeping.platform.booking.application.command;

/**
 * 取消订单命令。
 *
 * @param reasonCode 取消原因编码，可空
 * @param reasonText 取消原因描述，可空
 */
public record BookingCancelCommand(String reasonCode, String reasonText) {
}
