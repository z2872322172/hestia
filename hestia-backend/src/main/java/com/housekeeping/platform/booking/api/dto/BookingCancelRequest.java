package com.housekeeping.platform.booking.api.dto;

/**
 * 取消订单请求。
 *
 * @param reasonCode 取消原因编码，可空
 * @param reasonText 取消原因描述，可空
 */
public record BookingCancelRequest(String reasonCode, String reasonText) {
}
