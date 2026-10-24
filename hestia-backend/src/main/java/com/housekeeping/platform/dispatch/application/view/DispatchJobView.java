package com.housekeeping.platform.dispatch.application.view;

import com.housekeeping.platform.dispatch.persistence.entity.DispatchJobEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 派单任务视图（运营端）：任务状态 + 候选评分快照 + 分配历史链。
 */
public record DispatchJobView(long id,
                              String jobNo,
                              long bookingId,
                              String status,
                              Long currentAssignmentId,
                              int candidateCount,
                              int attemptCount,
                              String manualRequiredReason,
                              LocalDateTime lastAssignedAt,
                              List<DispatchCandidateView> candidates,
                              List<AssignmentView> assignments) {

    /** 从任务实体与候选/分配列表构建视图。 */
    public static DispatchJobView from(DispatchJobEntity job,
                                       List<DispatchCandidateView> candidates,
                                       List<AssignmentView> assignments) {
        return new DispatchJobView(
                job.getId(),
                job.getJobNo(),
                job.getBookingId(),
                job.getStatus(),
                job.getCurrentAssignmentId(),
                job.getCandidateCount(),
                job.getAttemptCount(),
                job.getManualRequiredReason(),
                job.getLastAssignedAt(),
                candidates,
                assignments);
    }
}
