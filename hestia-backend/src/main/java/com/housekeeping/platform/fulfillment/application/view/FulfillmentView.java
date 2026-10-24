package com.housekeeping.platform.fulfillment.application.view;

import com.housekeeping.platform.fulfillment.persistence.entity.ServiceChecklistItemEntity;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceEvidenceEntity;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceExceptionEntity;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceRecordEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 履约聚合视图：记录 + 检查项 + 证据 + 异常（三端通用）。
 */
public record FulfillmentView(long id,
                              String recordNo,
                              long bookingId,
                              long workerId,
                              String status,
                              LocalDateTime departedAt,
                              LocalDateTime checkInAt,
                              BigDecimal checkInLongitude,
                              BigDecimal checkInLatitude,
                              String checkInSource,
                              LocalDateTime startedAt,
                              LocalDateTime finishedAt,
                              LocalDateTime completionRequestedAt,
                              LocalDateTime confirmationDeadline,
                              LocalDateTime customerConfirmedAt,
                              String confirmSource,
                              String serviceResult,
                              List<ChecklistItemView> checklist,
                              List<EvidenceView> evidences,
                              List<ExceptionView> exceptions) {

    /**
     * 聚合构建视图。
     *
     * @param record     履约记录
     * @param checklist  检查项列表
     * @param evidences  证据列表
     * @param exceptions 异常列表
     * @return 聚合视图
     */
    public static FulfillmentView from(ServiceRecordEntity record,
                                       List<ChecklistItemView> checklist,
                                       List<EvidenceView> evidences,
                                       List<ExceptionView> exceptions) {
        return new FulfillmentView(
                record.getId(), record.getRecordNo(), record.getBookingId(),
                record.getWorkerId(), record.getStatus(), record.getDepartedAt(),
                record.getCheckInAt(), record.getCheckInLongitude(),
                record.getCheckInLatitude(), record.getCheckInSource(),
                record.getStartedAt(), record.getFinishedAt(),
                record.getCompletionRequestedAt(), record.getConfirmationDeadline(),
                record.getCustomerConfirmedAt(), record.getConfirmSource(),
                record.getServiceResult(), checklist, evidences, exceptions);
    }

    /** 检查项视图。 */
    public record ChecklistItemView(long id, Long sourceStandardItemId, String standardGroup,
                                    String itemName, String description, boolean required,
                                    boolean evidenceRequired, boolean completed,
                                    LocalDateTime completedAt, String remark, int sortOrder) {

        /** 从检查项实体构建视图。 */
        public static ChecklistItemView from(ServiceChecklistItemEntity entity) {
            return new ChecklistItemView(entity.getId(), entity.getSourceStandardItemId(),
                    entity.getStandardGroup(), entity.getItemName(), entity.getDescription(),
                    Boolean.TRUE.equals(entity.getRequired()),
                    Boolean.TRUE.equals(entity.getEvidenceRequired()),
                    Boolean.TRUE.equals(entity.getCompleted()),
                    entity.getCompletedAt(), entity.getRemark(), entity.getSortOrder());
        }
    }

    /** 证据视图（只含文件 ID 与元数据，不含 URL，docs/modules/09 第 9.2 节）。 */
    public record EvidenceView(long id, Long checklistItemId, String evidenceType,
                               long fileId, String description, Long uploadedByAccountId,
                               LocalDateTime createdAt) {

        /** 从证据实体构建视图。 */
        public static EvidenceView from(ServiceEvidenceEntity entity) {
            return new EvidenceView(entity.getId(), entity.getChecklistItemId(),
                    entity.getEvidenceType(), entity.getFileId(), entity.getDescription(),
                    entity.getUploadedByAccountId(), entity.getCreatedAt());
        }
    }

    /** 异常视图。 */
    public record ExceptionView(long id, String exceptionNo, String exceptionType,
                                String description, String status, Long reportedByAccountId,
                                Long handledByAccountId, String resolution,
                                LocalDateTime reportedAt, LocalDateTime resolvedAt) {

        /** 从异常实体构建视图。 */
        public static ExceptionView from(ServiceExceptionEntity entity) {
            return new ExceptionView(entity.getId(), entity.getExceptionNo(),
                    entity.getExceptionType(), entity.getDescription(), entity.getStatus(),
                    entity.getReportedByAccountId(), entity.getHandledByAccountId(),
                    entity.getResolution(), entity.getReportedAt(), entity.getResolvedAt());
        }
    }
}
