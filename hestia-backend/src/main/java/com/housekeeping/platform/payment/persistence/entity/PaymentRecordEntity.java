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
 * 支付记录，对应表 {@code payment_record}（docs/modules/10 第 12.1 节）。
 *
 * <p>生成列 {@code success_booking_id} 在库内保证一单最多一条成功支付，
 * Java 侧不映射该列。</p>
 */
@Data
@TableName("payment_record")
public class PaymentRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private Long bookingId;

    private Long accountId;

    private Integer attemptNo;

    /** MOCK / WECHAT / ALIPAY。 */
    private String channel;

    private BigDecimal amount;

    private String currency;

    /** 见 {@code PaymentStatus}。 */
    private String status;

    private String thirdPartyTradeNo;

    private String idempotencyKey;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
