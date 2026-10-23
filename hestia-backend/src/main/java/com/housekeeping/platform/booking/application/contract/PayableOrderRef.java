package com.housekeeping.platform.booking.application.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可支付订单摘要（支付模块跨模块契约，docs/modules/10 第 7.1 节）。
 *
 * <p>由订单模块校验"这单能不能付、是不是这个客户的"，支付模块只拿结果——
 * 归属与状态校验必须留在订单模块内做，漏一次就是越权支付别人的订单。</p>
 *
 * @param bookingId       订单 ID
 * @param orderNo         订单号
 * @param payableAmount   服务端应付金额（客户端金额只用于展示）
 * @param paymentDeadline 支付截止时间
 */
public record PayableOrderRef(long bookingId, String orderNo, BigDecimal payableAmount,
                              LocalDateTime paymentDeadline) {
}
