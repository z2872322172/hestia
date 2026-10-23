package com.housekeeping.platform.payment.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调日志，对应表 {@code payment_callback_log}（docs/modules/10 第 12.3 节）。
 *
 * <p>不保存完整敏感报文，只保存报文哈希与必要业务号；
 * {@code (channel, callback_id)} 唯一，作为回调幂等的"占位claim"——
 * 先插 RECEIVED 抢占，重复回调插入冲突即判定重复。</p>
 */
@Data
@TableName("payment_callback_log")
public class PaymentCallbackLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channel;

    private String callbackId;

    private String paymentNo;

    /** 报文哈希（SHA-256 十六进制，ascii 定长 64）。 */
    private String payloadHash;

    /** RECEIVED / SUCCESS / DUPLICATE / FAILED。 */
    private String result;

    private String errorMessage;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}
