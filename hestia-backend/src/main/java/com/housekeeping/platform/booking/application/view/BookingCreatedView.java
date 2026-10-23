package com.housekeeping.platform.booking.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建订单响应。
 *
 * @param orderNo         订单号
 * @param orderStatus     订单状态（创建后为 PENDING_PAYMENT）
 * @param payableAmount   应付金额
 * @param paymentDeadline 支付截止时间
 */
public record BookingCreatedView(String orderNo, String orderStatus,
                                 BigDecimal payableAmount, LocalDateTime paymentDeadline) {
}
