package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后工单，对应表 {@code after_sale_ticket}（docs/modules/11 第 11.5 节）。
 *
 * <p>核心边界：工单只形成结论（resolution_type / responsibility），
 * 不直接修改订单主状态与支付流水；发生退款时只保存 refund_id 引用，
 * 由支付模块执行（docs 第 1、2.7 条）。</p>
 */
@Data
@TableName("after_sale_ticket")
public class AfterSaleTicketEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private Long bookingId;

    /** 关联履约记录（可空：订单可能未产生履约记录）。 */
    private Long serviceRecordId;

    private Long customerId;

    /** 被投诉人员（可空：未指派或与人员无关）。 */
    private Long workerId;

    /** QUALITY_COMPLAINT / LATE_ARRIVAL / DAMAGE / REWORK / REFUND_REQUEST / SERVICE_ATTITUDE / OTHER。 */
    private String ticketType;

    private String description;

    /** LOW / NORMAL / HIGH / URGENT。 */
    private String priority;

    /** 见 {@code TicketStatus}。 */
    private String status;

    /** 受理运营账号 ID。 */
    private Long handlerId;

    /** 处理时限（按优先级计算）。 */
    private LocalDateTime dueAt;

    /** REFUND / PARTIAL_REFUND / REWORK / COMPENSATION / EXPLANATION / NO_ACTION。 */
    private String resolutionType;

    private String resolutionText;

    /** UNDETERMINED / COMPANY / WORKER / CUSTOMER / SHARED / NONE。 */
    private String responsibility;

    /** 退款记录 ID（仅引用，退款执行在支付模块）。 */
    private Long refundId;

    /** 返工新订单 ID（首版预留）。 */
    private Long reworkBookingId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime submittedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
