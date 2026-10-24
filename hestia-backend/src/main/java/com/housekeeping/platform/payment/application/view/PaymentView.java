package com.housekeeping.platform.payment.application.view;

import com.housekeeping.platform.payment.persistence.entity.PaymentRecordEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单视图（客户 / 运营共用）。
 *
 * @param paymentId         支付单主键（内部定位用）
 * @param paymentNo         支付单号
 * @param bookingId         订单 ID
 * @param attemptNo         尝试序号
 * @param channel           支付渠道
 * @param amount            支付金额
 * @param currency          币种
 * @param status            支付状态
 * @param thirdPartyTradeNo 渠道交易号（脱敏场景下仍为内部流水号）
 * @param paidAt            支付成功时间
 * @param closedAt          关闭时间
 * @param createdAt         创建时间
 */
public record PaymentView(Long paymentId, String paymentNo, Long bookingId, Integer attemptNo,
                          String channel, BigDecimal amount, String currency, String status,
                          String thirdPartyTradeNo, LocalDateTime paidAt, LocalDateTime closedAt,
                          LocalDateTime createdAt) {

    /**
     * 从实体构造视图。
     *
     * @param entity 支付记录实体
     * @return 视图
     */
    public static PaymentView from(PaymentRecordEntity entity) {
        return new PaymentView(entity.getId(), entity.getPaymentNo(), entity.getBookingId(),
                entity.getAttemptNo(), entity.getChannel(), entity.getAmount(),
                entity.getCurrency(), entity.getStatus(), entity.getThirdPartyTradeNo(),
                entity.getPaidAt(), entity.getClosedAt(), entity.getCreatedAt());
    }

    /**
     * 重复回调的幂等回放视图（不暴露任何可变信息）。
     *
     * @param paymentNo 支付单号
     * @return 视图
     */
    public static PaymentView duplicateCallback(String paymentNo) {
        return new PaymentView(null, paymentNo, null, null, null, null, null,
                "DUPLICATE_CALLBACK", null, null, null, null);
    }
}
