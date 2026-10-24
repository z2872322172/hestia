package com.housekeeping.platform.dispatch.application;

import com.housekeeping.platform.dispatch.application.contract.DispatchOperationsStatsService;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchJobMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.OrderAssignmentMapper;
import com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 派单统计契约实现（docs/modules/13 第 18.2 节）。
 *
 * <p>只读聚合，供运营日报任务与看板使用。</p>
 */
@Service
@RequiresPersistence
public class DispatchStatsService implements DispatchOperationsStatsService {

    private static final String JOB_STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";

    private static final String JOB_STATUS_MANUAL_REQUIRED = "MANUAL_REQUIRED";

    private final OrderAssignmentMapper assignmentMapper;

    private final DispatchJobMapper jobMapper;

    public DispatchStatsService(OrderAssignmentMapper assignmentMapper,
                                DispatchJobMapper jobMapper) {
        this.assignmentMapper = assignmentMapper;
        this.jobMapper = jobMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentDateCountRow> autoSuccessDaily(LocalDate from, LocalDate to) {
        return assignmentMapper.selectAutoSuccessDaily(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentDateCountRow> rejectedDaily(LocalDate from, LocalDate to) {
        return assignmentMapper.selectRejectedDaily(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentDateCountRow> timeoutDaily(LocalDate from, LocalDate to) {
        return assignmentMapper.selectExpiredDaily(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentDateCountRow> manualDaily(LocalDate from, LocalDate to) {
        return assignmentMapper.selectManualDaily(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public DispatchQueueStat queueStat(Collection<Long> areaIds) {
        return new DispatchQueueStat(
                jobMapper.count(JOB_STATUS_PENDING_ACCEPT),
                jobMapper.count(JOB_STATUS_MANUAL_REQUIRED));
    }
}
