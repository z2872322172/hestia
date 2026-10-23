package com.housekeeping.platform.booking.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单取消记录，对应表 {@code booking_cancellation}（docs/modules/07 第 15.5 节）。
 *
 * <p>唯一键 {@code booking_id}：每单最多取消一次，重复取消在状态机层已被拦截。</p>
 */
@Data
@TableName("booking_cancellation")
public class BookingCancellationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookingId;

    private String reasonCode;

    private String reasonText;

    private Long canceledByAccountId;

    private String canceledByRole;

    private BigDecimal feeAmount;

    private BigDecimal refundableAmount;

    /** 取消时订单上的取消规则快照。 */
    private String policySnapshotJson;

    private LocalDateTime createdAt;
}
