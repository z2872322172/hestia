package com.housekeeping.platform.dispatch.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单人员分配记录，对应表 {@code order_assignment}（docs/modules/08 第 13.4 节）。
 *
 * <p>一次分配一条记录，改派时旧记录置 REASSIGNED、新记录通过
 * {@code replaced_assignment_id} 指向旧记录，形成分配链。</p>
 */
@Data
@TableName("order_assignment")
public class OrderAssignmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String assignmentNo;

    private Long dispatchJobId;

    private Long bookingId;

    private Long workerId;

    /** AUTO / MANUAL / REASSIGN。 */
    private String assignType;

    /** WAITING_PAYMENT / PENDING_ACCEPT / ACCEPTED / REJECTED / EXPIRED / REASSIGNED / CANCELED。 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long assignedByAccountId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime assignedAt;

    private LocalDateTime acceptDeadline;

    private LocalDateTime acceptedAt;

    private LocalDateTime rejectedAt;

    private String rejectReasonCode;

    private String rejectReasonText;

    /** 改派时指向被替换的分配记录。 */
    private Long replacedAssignmentId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
