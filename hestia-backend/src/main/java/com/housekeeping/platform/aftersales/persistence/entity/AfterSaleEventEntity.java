package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后工单时间线，对应表 {@code after_sale_event}（docs/modules/11 第 11.6 节）。
 *
 * <p>每次状态变更与关键动作追加一条事件，只增不改。</p>
 */
@Data
@TableName("after_sale_event")
public class AfterSaleEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    /** CREATED / ACCEPTED / PENDING_CUSTOMER / BACK_PROCESSING / RESOLVED / REJECTED / CLOSED / SLA_EXPIRED 等。 */
    private String eventType;

    private String fromStatus;

    private String toStatus;

    private String description;

    private Long operatorAccountId;

    /** CUSTOMER / WORKER / OPERATOR / SYSTEM。 */
    private String operatorRole;

    private LocalDateTime createdAt;
}
