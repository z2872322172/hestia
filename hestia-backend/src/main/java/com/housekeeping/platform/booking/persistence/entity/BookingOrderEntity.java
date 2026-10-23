package com.housekeeping.platform.booking.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约订单主表，对应表 {@code booking_order}（docs/modules/07 第 15.1 节）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>客户/目录/片区/人员/时段一律快照或逻辑外键，跨模块不做级联；</li>
 *   <li>{@code order_status} 主状态由 {@code BookingStatus} 状态机约束，
 *       五个摘要状态（payment/assignment/fulfillment/after_sale/review）事实来源在各自模块；</li>
 *   <li>三个 JSON 快照列（报价/服务标准/取消规则）在 Java 侧用 String 承载，
 *       由应用层用 ObjectMapper 序列化，避免 MyBatis 类型处理器引入隐式行为。</li>
 * </ul>
 */
@Data
@TableName("booking_order")
public class BookingOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long customerId;

    private String customerNoSnapshot;

    private Long serviceItemId;

    private Long serviceItemVersion;

    private String serviceItemNoSnapshot;

    private String serviceNameSnapshot;

    private Long serviceAreaId;

    private String serviceAreaNameSnapshot;

    private Long addressId;

    private Long addressVersion;

    private String addressSnapshotJson;

    private String contactNameSnapshot;

    private String contactPhoneSnapshot;

    private LocalDate appointmentDate;

    private Long serviceWindowId;

    private String serviceWindowNameSnapshot;

    private LocalDateTime appointmentStart;

    private LocalDateTime appointmentEnd;

    private Long workerSlotId;

    private Long currentWorkerId;

    /** 见 {@code BookingStatus}。 */
    private String orderStatus;

    /** UNPAID / PAID / REFUNDING / PARTIALLY_REFUNDED / REFUNDED。 */
    private String paymentStatus;

    /** WAITING_PAYMENT / PENDING_ACCEPT / ACCEPTED / REASSIGNING / MANUAL_REQUIRED / CANCELED。 */
    private String assignmentStatus;

    /** NOT_STARTED / EN_ROUTE / CHECKED_IN / IN_SERVICE / PENDING_CONFIRM / COMPLETED / EXCEPTION。 */
    private String fulfillmentStatus;

    /** NONE / PROCESSING / RESOLVED / CLOSED。 */
    private String afterSaleStatus;

    /** NOT_REVIEWED / REVIEWED。 */
    private String reviewStatus;

    private BigDecimal serviceAmount;

    private BigDecimal addonAmount;

    private BigDecimal materialAmount;

    private BigDecimal travelFee;

    private BigDecimal discountAmount;

    private BigDecimal payableAmount;

    private BigDecimal paidAmount;

    private BigDecimal refundedAmount;

    private String currency;

    private String priceSnapshotJson;

    private String serviceStandardSnapshotJson;

    private String cancellationPolicySnapshotJson;

    private LocalDateTime paymentDeadline;

    /** CUSTOMER / OPERATOR / SYSTEM。 */
    private String source;

    private String cancelReasonCode;

    private String cancelReasonText;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime paidAt;

    private LocalDateTime assignmentAcceptedAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
