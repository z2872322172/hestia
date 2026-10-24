package com.housekeeping.platform.dispatch.application.view;

import com.housekeeping.platform.dispatch.persistence.entity.OrderAssignmentEntity;

import java.time.LocalDateTime;

/**
 * 订单人员分配视图（人员端 / 运营端通用）。
 *
 * <p>不含客户地址或电话（docs/modules/08 第 4.2 节脱敏要求），
 * 服务时间等订单信息由订单侧任务接口提供。</p>
 */
public record AssignmentView(long id,
                             String assignmentNo,
                             long bookingId,
                             long workerId,
                             String assignType,
                             String status,
                             String dispatchReason,
                             LocalDateTime assignedAt,
                             LocalDateTime acceptDeadline,
                             LocalDateTime acceptedAt,
                             LocalDateTime rejectedAt,
                             String rejectReasonCode,
                             String rejectReasonText,
                             Long replacedAssignmentId) {

    /** 从分配实体构建视图。 */
    public static AssignmentView from(OrderAssignmentEntity entity) {
        return new AssignmentView(
                entity.getId(),
                entity.getAssignmentNo(),
                entity.getBookingId(),
                entity.getWorkerId(),
                entity.getAssignType(),
                entity.getStatus(),
                entity.getDispatchReason(),
                entity.getAssignedAt(),
                entity.getAcceptDeadline(),
                entity.getAcceptedAt(),
                entity.getRejectedAt(),
                entity.getRejectReasonCode(),
                entity.getRejectReasonText(),
                entity.getReplacedAssignmentId());
    }
}
