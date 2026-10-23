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
 * 订单派单任务，对应表 {@code dispatch_job}（docs/modules/08 第 13.2 节）。
 *
 * <p>唯一键 {@code booking_id}：一单一个派单任务。
 * 状态流转全部使用条件更新（WHERE status = 期望值），影响行数 0 即为冲突。</p>
 */
@Data
@TableName("dispatch_job")
public class DispatchJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobNo;

    private Long bookingId;

    private Long policyId;

    /** WAITING_PAYMENT / READY / PENDING_ACCEPT / ACCEPTED / REASSIGNING / MANUAL_REQUIRED / CANCELED。 */
    private String status;

    /** 当前生效的分配记录 ID（order_assignment.id）。 */
    private Long currentAssignmentId;

    private Integer candidateCount;

    private Integer attemptCount;

    private String manualRequiredReason;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime lastAssignedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
