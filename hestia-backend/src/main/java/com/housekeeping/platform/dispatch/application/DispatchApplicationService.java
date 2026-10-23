package com.housekeeping.platform.dispatch.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchCandidateEntity;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchJobEntity;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchPolicyEntity;
import com.housekeeping.platform.dispatch.persistence.entity.OrderAssignmentEntity;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchCandidateMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchJobMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchPolicyMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.OrderAssignmentMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 派单应用服务（docs/modules/08 第 8 节，批次 3 骨架）。
 *
 * <p>本批次实现订单生命周期驱动的四个入口：
 * 下单建任务、支付激活、取消、改期改派。
 * 人员接单/拒单与主动改派的完整评分算法（docs 第 6、7、9 节）在批次 4 扩展；
 * 当前候选评分使用简单递减分（第 1 名 100 分、之后依次减 1），
 * 保证候选快照、名次与分配链的数据结构先立起来。</p>
 *
 * <p>所有状态流转使用 Mapper 条件更新，影响行数 0 抛 {@code DISPATCH_JOB_CONFLICT}
 * ——派单与订单分属两个模块，这里只保证自己的表不脏，订单侧由订单事务统一回滚。</p>
 */
@Service
@RequiresPersistence
public class DispatchApplicationService {

    private static final String SEQ_DISPATCH = "DISPATCH";

    private static final String SEQ_ASSIGNMENT = "ASSIGN";

    private static final String PREFIX_JOB = "DSP";

    private static final String PREFIX_ASSIGNMENT = "ASG";

    private final DispatchJobMapper jobMapper;

    private final DispatchCandidateMapper candidateMapper;

    private final DispatchPolicyMapper policyMapper;

    private final OrderAssignmentMapper assignmentMapper;

    private final BusinessNoGenerator noGenerator;

    private final ObjectMapper objectMapper;

    public DispatchApplicationService(DispatchJobMapper jobMapper,
                                      DispatchCandidateMapper candidateMapper,
                                      DispatchPolicyMapper policyMapper,
                                      OrderAssignmentMapper assignmentMapper,
                                      BusinessNoGenerator noGenerator,
                                      ObjectMapper objectMapper) {
        this.jobMapper = jobMapper;
        this.candidateMapper = candidateMapper;
        this.policyMapper = policyMapper;
        this.assignmentMapper = assignmentMapper;
        this.noGenerator = noGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建订单时建派单任务（docs/modules/08 第 8.1 节）。
     *
     * <p>在订单创建事务内调用：候选按传入顺序落库（时段库存顺序即默认名次），
     * 第一名标记 SELECTED 并生成 WAITING_PAYMENT 分配记录。</p>
     *
     * @param bookingId        订单 ID
     * @param orderedWorkerIds 有序候选人员（第一个为选中人员）
     * @param selectedWorkerId 选中人员 ID
     * @param accountId        发起账号 ID
     * @return 派单任务
     * @throws BusinessException 无 ACTIVE 派单策略或候选列表为空
     */
    @Transactional
    public DispatchJobEntity createForBooking(long bookingId, List<Long> orderedWorkerIds,
                                              long selectedWorkerId, Long accountId) {
        if (orderedWorkerIds == null || orderedWorkerIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_NOT_AVAILABLE);
        }
        DispatchPolicyEntity policy = requireActivePolicy();
        DispatchJobEntity job = new DispatchJobEntity();
        job.setJobNo(noGenerator.next(SEQ_DISPATCH, PREFIX_JOB));
        job.setBookingId(bookingId);
        job.setPolicyId(policy.getId());
        job.setStatus("WAITING_PAYMENT");
        job.setCandidateCount(orderedWorkerIds.size());
        job.setAttemptCount(0);
        job.setCreatedByAccountId(accountId);
        job.setUpdatedByAccountId(accountId);
        jobMapper.insert(job);

        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < orderedWorkerIds.size(); index++) {
            Long workerId = orderedWorkerIds.get(index);
            DispatchCandidateEntity candidate = new DispatchCandidateEntity();
            candidate.setDispatchJobId(job.getId());
            candidate.setPolicyId(policy.getId());
            candidate.setWorkerId(workerId);
            candidate.setRankNo(index + 1);
            candidate.setTotalScore(simpleScore(index));
            candidate.setScoreJson(simpleScoreJson(index));
            candidate.setStatus(workerId == selectedWorkerId ? "SELECTED" : "GENERATED");
            candidateMapper.insert(candidate);
        }

        OrderAssignmentEntity assignment = insertAssignment(job.getId(), bookingId,
                selectedWorkerId, "AUTO", null, accountId,
                now, null);
        jobMapper.markCurrentAssignment(job.getId(), assignment.getId(), now, "WAITING_PAYMENT");
        return job;
    }

    /**
     * 支付成功激活派单（docs/modules/08 第 8.2 节）：
     * 任务与分配 WAITING_PAYMENT → PENDING_ACCEPT，并设置接单截止时间。
     *
     * @param bookingId 订单 ID
     * @return 当前分配的服务人员 ID（订单侧用它回填 current_worker_id）
     * @throws BusinessException 派单任务或分配状态不允许激活
     */
    @Transactional
    public long activateForPayment(long bookingId) {
        DispatchJobEntity job = requireByBookingId(bookingId);
        DispatchPolicyEntity policy = policyMapper.selectById(job.getPolicyId());
        int timeoutMinutes = policy != null ? policy.getAcceptTimeoutMinutes() : 10;
        if (jobMapper.activateForPayment(bookingId) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        if (assignmentMapper.activateForPayment(bookingId,
                LocalDateTime.now().plusMinutes(timeoutMinutes)) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        OrderAssignmentEntity assignment = assignmentMapper.selectLatestByBookingId(bookingId);
        return assignment != null && assignment.getWorkerId() != null ? assignment.getWorkerId() : 0L;
    }

    /**
     * 取消订单时取消派单（docs/modules/08 第 8.4 节的订单侧）。
     *
     * <p>待支付/待接单阶段的任务与分配一起取消；
     * 已接单订单的取消走履约/售后，不由本方法处理。</p>
     *
     * @param bookingId 订单 ID
     */
    @Transactional
    public void cancelForBooking(long bookingId) {
        jobMapper.cancelForBooking(bookingId);
        assignmentMapper.cancelForBooking(bookingId);
    }

    /**
     * 改期改派（docs/modules/08 第 9 节的订单侧）：
     * 旧分配 PENDING_ACCEPT → REASSIGNED，新分配指向旧记录形成改派链，
     * 任务保持 PENDING_ACCEPT 不变。
     *
     * @param bookingId   订单 ID
     * @param newWorkerId 改期后选中的人员 ID
     * @param accountId   发起账号 ID
     * @throws BusinessException 派单任务不存在或旧分配不在待接单状态
     */
    @Transactional
    public void rescheduleForBooking(long bookingId, long newWorkerId, Long accountId) {
        DispatchJobEntity job = requireByBookingId(bookingId);
        DispatchPolicyEntity policy = policyMapper.selectById(job.getPolicyId());
        int timeoutMinutes = policy != null ? policy.getAcceptTimeoutMinutes() : 10;
        OrderAssignmentEntity current = assignmentMapper.selectLatestByBookingId(bookingId);
        if (current == null || !"PENDING_ACCEPT".equals(current.getStatus())) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        if (assignmentMapper.reassignFromPending(current.getId()) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        OrderAssignmentEntity replacement = insertAssignment(job.getId(), bookingId,
                newWorkerId, "REASSIGN", current.getId(), accountId,
                now, now.plusMinutes(timeoutMinutes));
        jobMapper.markCurrentAssignment(job.getId(), replacement.getId(), now, "PENDING_ACCEPT");
    }

    /**
     * 按订单查询派单任务。
     *
     * @param bookingId 订单 ID
     * @return 任务或 null
     */
    @Transactional(readOnly = true)
    public DispatchJobEntity findByBookingId(long bookingId) {
        return jobMapper.selectByBookingId(bookingId);
    }

    private DispatchJobEntity requireByBookingId(long bookingId) {
        DispatchJobEntity job = jobMapper.selectByBookingId(bookingId);
        if (job == null) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT, "派单任务不存在");
        }
        return job;
    }

    private DispatchPolicyEntity requireActivePolicy() {
        DispatchPolicyEntity policy = policyMapper.selectActive();
        if (policy == null) {
            throw new IllegalStateException("未配置 ACTIVE 派单策略（dispatch_policy）");
        }
        return policy;
    }

    private OrderAssignmentEntity insertAssignment(long jobId, long bookingId, long workerId,
                                                   String assignType, Long replacedAssignmentId,
                                                   Long accountId, LocalDateTime assignedAt,
                                                   LocalDateTime acceptDeadline) {
        OrderAssignmentEntity assignment = new OrderAssignmentEntity();
        assignment.setAssignmentNo(noGenerator.next(SEQ_ASSIGNMENT, PREFIX_ASSIGNMENT));
        assignment.setDispatchJobId(jobId);
        assignment.setBookingId(bookingId);
        assignment.setWorkerId(workerId);
        assignment.setAssignType(assignType);
        assignment.setStatus("WAITING_PAYMENT");
        assignment.setAssignedByAccountId(accountId);
        assignment.setCreatedByAccountId(accountId);
        assignment.setUpdatedByAccountId(accountId);
        assignment.setAssignedAt(assignedAt);
        assignment.setAcceptDeadline(acceptDeadline);
        assignment.setReplacedAssignmentId(replacedAssignmentId);
        assignmentMapper.insert(assignment);
        return assignment;
    }

    /** 简单递减分：第 1 名 100.000，之后每位减 1（批次 4 换成加权评分）。 */
    private BigDecimal simpleScore(int zeroBasedRank) {
        return BigDecimal.valueOf(100 - zeroBasedRank).setScale(3);
    }

    /** 候选评分快照：说明本批次使用默认顺序评分，不引入伪明细。 */
    private String simpleScoreJson(int zeroBasedRank) {
        try {
            return objectMapper.writeValueAsString(new SimpleScore(
                    zeroBasedRank + 1, BigDecimal.valueOf(100 - zeroBasedRank),
                    "DEFAULT_ORDER"));
        } catch (Exception exception) {
            throw new IllegalStateException("候选评分 JSON 序列化失败", exception);
        }
    }

    private record SimpleScore(int rank, BigDecimal totalScore, String basis) {
    }
}
