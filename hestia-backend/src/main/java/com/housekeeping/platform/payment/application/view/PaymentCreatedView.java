package com.housekeeping.platform.payment.application.view;

import java.math.BigDecimal;

/**
 * 支付创建结果（客户端据此拉起收银台；Mock 渠道直接进回调）。
 *
 * @param paymentNo 支付单号
 * @param amount    应付金额（服务端口径）
 * @param channel   支付渠道
 * @param status    当前支付状态（通常 PAYING）
 */
public record PaymentCreatedView(String paymentNo, BigDecimal amount, String channel,
                                 String status) {
}
