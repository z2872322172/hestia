package com.housekeeping.platform.aftersales.application.view;

import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleAttachmentEntity;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleEventEntity;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleTicketEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后工单视图：工单 + 时间线 + 附件（三端通用）。
 */
public record AfterSaleTicketView(long id,
                                  String ticketNo,
                                  long bookingId,
                                  Long serviceRecordId,
                                  long customerId,
                                  Long workerId,
                                  String ticketType,
                                  String description,
                                  String priority,
                                  String status,
                                  Long handlerId,
                                  LocalDateTime dueAt,
                                  String resolutionType,
                                  String resolutionText,
                                  String responsibility,
                                  Long refundId,
                                  LocalDateTime submittedAt,
                                  LocalDateTime resolvedAt,
                                  LocalDateTime closedAt,
                                  List<EventView> events,
                                  List<AttachmentView> attachments) {

    /**
     * 聚合构建视图。
     *
     * @param ticket      工单实体
     * @param events      时间线
     * @param attachments 附件
     * @return 工单视图
     */
    public static AfterSaleTicketView from(AfterSaleTicketEntity ticket,
                                           List<AfterSaleEventEntity> events,
                                           List<AfterSaleAttachmentEntity> attachments) {
        return new AfterSaleTicketView(
                ticket.getId(), ticket.getTicketNo(), ticket.getBookingId(),
                ticket.getServiceRecordId(), ticket.getCustomerId(), ticket.getWorkerId(),
                ticket.getTicketType(), ticket.getDescription(), ticket.getPriority(),
                ticket.getStatus(), ticket.getHandlerId(), ticket.getDueAt(),
                ticket.getResolutionType(), ticket.getResolutionText(),
                ticket.getResponsibility(), ticket.getRefundId(),
                ticket.getSubmittedAt(), ticket.getResolvedAt(), ticket.getClosedAt(),
                events.stream().map(EventView::from).toList(),
                attachments.stream().map(AttachmentView::from).toList());
    }

    /** 时间线事件视图。 */
    public record EventView(long id, String eventType, String fromStatus, String toStatus,
                            String description, Long operatorAccountId, String operatorRole,
                            LocalDateTime createdAt) {

        /** 从实体构建视图。 */
        public static EventView from(AfterSaleEventEntity entity) {
            return new EventView(entity.getId(), entity.getEventType(), entity.getFromStatus(),
                    entity.getToStatus(), entity.getDescription(), entity.getOperatorAccountId(),
                    entity.getOperatorRole(), entity.getCreatedAt());
        }
    }

    /** 附件视图。 */
    public record AttachmentView(long id, long fileId, String attachmentType,
                                 long uploadedByAccountId, LocalDateTime createdAt) {

        /** 从实体构建视图。 */
        public static AttachmentView from(AfterSaleAttachmentEntity entity) {
            return new AttachmentView(entity.getId(), entity.getFileId(),
                    entity.getAttachmentType(), entity.getUploadedByAccountId(),
                    entity.getCreatedAt());
        }
    }
}
