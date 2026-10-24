package com.housekeeping.platform.payment.application.view;

import com.housekeeping.platform.payment.persistence.entity.RefundRecordEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单视图（客户 / 运营共用）。
 *
 * @param refundNo       退款单号
 * @param bookingId      订单 ID
 * @param paymentId      支付记录 ID
 * @param amount         退款金额
 * @param reasonCode     原因编码
 * @param reasonText     原因描述
 * @param status         退款状态
 * @param reviewedAt     审批时间
 * @param refundedAt     退款完成时间
 * @param failureReason  失败原因
 * @param createdAt      创建时间
 */
public record RefundView(String refundNo, Long bookingId, Long paymentId, BigDecimal amount,
                         String reasonCode, String reasonText, String status,
                         LocalDateTime reviewedAt, LocalDateTime refundedAt, String failureReason,
                         LocalDateTime createdAt) {

    /**
     * 从实体构造视图。
     *
     * @param entity 退款记录实体
     * @return 视图
     */
    public static RefundView from(RefundRecordEntity entity) {
        return new RefundView(entity.getRefundNo(), entity.getBookingId(), entity.getPaymentId(),
                entity.getAmount(), entity.getReasonCode(), entity.getReasonText(),
                entity.getStatus(), entity.getReviewedAt(), entity.getRefundedAt(),
                entity.getFailureReason(), entity.getCreatedAt());
    }
}
