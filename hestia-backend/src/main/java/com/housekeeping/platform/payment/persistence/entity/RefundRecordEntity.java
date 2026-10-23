package com.housekeeping.platform.payment.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款记录，对应表 {@code refund_record}（docs/modules/10 第 12.2 节）。
 *
 * <p>申请（REQUESTED）、审批（APPROVED/REJECTED）与执行（PROCESSING/SUCCESS/FAILED）
 * 分开记录；{@code reviewed_by_account_id} 由 CHECK 约束保证终态必有审核人。</p>
 */
@Data
@TableName("refund_record")
public class RefundRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private Long bookingId;

    private Long paymentId;

    private BigDecimal amount;

    private String reasonCode;

    private String reasonText;

    /** 见 {@code RefundStatus}。 */
    private String status;

    private Long requestedByAccountId;

    private Long reviewedByAccountId;

    private LocalDateTime reviewedAt;

    private LocalDateTime refundedAt;

    private String failureReason;

    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
