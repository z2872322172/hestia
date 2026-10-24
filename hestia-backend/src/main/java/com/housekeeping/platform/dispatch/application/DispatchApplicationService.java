package com.housekeeping.platform.dispatch.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.application.view.AssignmentView;
import com.housekeeping.platform.dispatch.application.view.DispatchCandidateView;
import com.housekeeping.platform.dispatch.application.view.DispatchJobView;
import com.housekeeping.platform.dispatch.domain.DispatchScorePolicy;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchCandidateEntity;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchJobEntity;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchPolicyEntity;
import com.housekeeping.platform.dispatch.persistence.entity.OrderAssignmentEntity;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchCandidateMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchJobMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.DispatchPolicyMapper;
import com.housekeeping.platform.dispatch.persistence.mapper.OrderAssignmentMapper;
import com.housekeeping.platform.fulfillment.application.FulfillmentApplicationService;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.scheduling.application.WorkerSlotReservationService;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerSlotEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 派单应用服务（docs/modules/08 第 8、9 节）。
 *
 * <p>订单生命周期驱动的四个入口：下单建任务、支付激活、取消、改期改派；
 * 人员侧两个动作：接单、拒单（原因必填）；系统侧超时改派；
 * 运营侧手工派单与改派（原因必填，写入分配记录审计）。</p>
 *
 * <p>改派铁律（第 8.4 节）：拒单/超时不取消订单——先锁定新人员时段成功后
 * 才释放旧时段，再创建新分配并同步订单摘要；全部候选不可用转 MANUAL_REQUIRED，
 * 由运营手工处理。任务推进使用条件更新，影响行数 0 抛
 * {@code DISPATCH_JOB_CONFLICT}，并发的两条改派链路只有一条能落地。</p>
 *
 * <p>评分（第 7 节）：加权总分由 {@link DispatchScorePolicy} 纯函数计算，
 * 质量分来自 {@link DispatchQualityScoreProvider} 契约（当前中性 60），
 * 其余分项在候选筛选已过硬条件的前提下取中性值，快照落
 * {@code dispatch_candidate.score_json}。</p>
 */
@Service
@RequiresPersistence
public class DispatchApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DispatchApplicationService.class);

    private static final String SEQ_DISPATCH = "DISPATCH";

    private static final String SEQ_ASSIGNMENT = "ASSIGN";

    private static final String PREFIX_JOB = "DSP";

    private static final String PREFIX_ASSIGNMENT = "ASG";

    /** 硬条件外的中性分：区域/技能已被候选筛选保证，负载/连续性/偏好后续批次接入。 */
    private static final int NEUTRAL_SCORE = 60;

    private final DispatchJobMapper jobMapper;

    private final DispatchCandidateMapper candidateMapper;

    private final DispatchPolicyMapper policyMapper;

    private final OrderAssignmentMapper assignmentMapper;

    private final WorkerSlotReservationService slotReservation;

    private final DispatchQualityScoreProvider qualityScoreProvider;

    private final BookingAssignmentSummarySyncer summarySyncer;

    private final BusinessNoGenerator noGenerator;

    private final ObjectMapper objectMapper;

    /**
     * 履约服务：接单成功后创建履约记录。
     * 仅接单时使用，@Lazy 打破 booking → dispatch → fulfillment → booking 的启动环。
     */
    private final FulfillmentApplicationService fulfillmentService;

    public DispatchApplicationService(DispatchJobMapper jobMapper,
                                      DispatchCandidateMapper candidateMapper,
                                      DispatchPolicyMapper policyMapper,
                                      OrderAssignmentMapper assignmentMapper,
                                      WorkerSlotReservationService slotReservation,
                                      DispatchQualityScoreProvider qualityScoreProvider,
                                      BookingAssignmentSummarySyncer summarySyncer,
                                      BusinessNoGenerator noGenerator,
                                      ObjectMapper objectMapper,
                                      @Lazy FulfillmentApplicationService fulfillmentService) {
        this.jobMapper = jobMapper;
        this.candidateMapper = candidateMapper;
        this.policyMapper = policyMapper;
        this.assignmentMapper = assignmentMapper;
        this.slotReservation = slotReservation;
        this.qualityScoreProvider = qualityScoreProvider;
        this.summarySyncer = summarySyncer;
        this.noGenerator = noGenerator;
        this.objectMapper = objectMapper;
        this.fulfillmentService = fulfillmentService;
    }

    // ------------------------------------------------------------------
    // 订单生命周期驱动的派单
    // ------------------------------------------------------------------

    /**
     * 创建订单时建派单任务（docs/modules/08 第 8.1 节）。
     *
     * <p>候选按评分名次落库（总分降序、同分保持传入顺序的稳定排序），
     * 选中人员标记 SELECTED 并生成 WAITING_PAYMENT 分配记录。
     * 评分只决定候选名次快照，不改变当前选中人——时段已按选中人锁定，
     * 改派时按名次从高到低尝试其余候选。</p>
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

        List<ScoredCandidate> scored = scoreCandidates(policy, orderedWorkerIds);
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < scored.size(); index++) {
            ScoredCandidate item = scored.get(index);
            DispatchCandidateEntity candidate = new DispatchCandidateEntity();
            candidate.setDispatchJobId(job.getId());
            candidate.setPolicyId(policy.getId());
            candidate.setWorkerId(item.workerId());
            candidate.setRankNo(index + 1);
            candidate.setTotalScore(item.result().totalScore());
            candidate.setScoreJson(toScoreJson(item.result()));
            candidate.setStatus(item.workerId() == selectedWorkerId
                    ? "SELECTED" : "GENERATED");
            candidate.setGeneratedAt(now);
            candidateMapper.insert(candidate);
        }

        OrderAssignmentEntity assignment = insertAssignment(job.getId(), bookingId,
                selectedWorkerId, "AUTO", null, accountId, now, null,
                "WAITING_PAYMENT", null);
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
                now, now.plusMinutes(timeoutMinutes), "PENDING_ACCEPT", "RESCHEDULE");
        jobMapper.markCurrentAssignment(job.getId(), replacement.getId(), now, "PENDING_ACCEPT");
    }

    // ------------------------------------------------------------------
    // 人员接单 / 拒单（docs/modules/08 第 8.3、8.4 节）
    // ------------------------------------------------------------------

    /**
     * 人员接单：分配与任务 PENDING_ACCEPT → ACCEPTED，订单摘要同步 ACCEPTED。
     *
     * <p>接单截止时间已过直接拒绝（由超时任务负责改派），避免与
     * 超时改派链路竞争。任何一步条件更新 0 行都整体回滚。</p>
     *
     * @param workerId     接单人员 ID
     * @param assignmentNo 分配单号
     * @return 接单后的分配视图
     * @throws BusinessException 不存在/非本人/已过期/状态冲突
     */
    @Transactional
    public AssignmentView accept(long workerId, String assignmentNo) {
        OrderAssignmentEntity assignment = requireAssignment(assignmentNo);
        if (!assignment.getWorkerId().equals(workerId)) {
            // 非本人的分配与不存在同样按 404 处理，不泄露其他人员的派单
            throw new BusinessException(ErrorCode.DISPATCH_ASSIGNMENT_NOT_OWNED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (assignment.getAcceptDeadline() != null
                && assignment.getAcceptDeadline().isBefore(now)) {
            throw new BusinessException(ErrorCode.DISPATCH_ASSIGNMENT_EXPIRED);
        }
        if (assignmentMapper.accept(assignment.getId(), now) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        DispatchJobEntity job = requireJobById(assignment.getDispatchJobId());
        if (jobMapper.markAccepted(job.getId()) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        if (summarySyncer.markAccepted(assignment.getBookingId(), workerId) == 0) {
            // 订单已不在待接单摘要（取消/推进），接单不成立，整体回滚
            throw new BusinessException(ErrorCode.BOOKING_STATUS_CONFLICT);
        }
        // 接单成功即创建履约记录（docs/modules/09 第 6 节；改派时记录重置换绑）
        fulfillmentService.createForBooking(assignment.getBookingId(), workerId, null);
        return AssignmentView.from(assignmentMapper.selectById(assignment.getId()));
    }

    /**
     * 人员拒单（docs/modules/08 第 8.4 节）：分配 → REJECTED，
     * 释放时段、任务 → REASSIGNING 并自动尝试下一候选；
     * 无候选转 MANUAL_REQUIRED。拒单原因必填。
     *
     * @param workerId     拒单人员 ID
     * @param assignmentNo 分配单号
     * @param reasonCode   原因码
     * @param reasonText   原因文本（可空）
     * @return 拒单后的分配视图
     * @throws BusinessException 原因缺失 / 不存在 / 状态冲突
     */
    @Transactional
    public AssignmentView reject(long workerId, String assignmentNo,
                                 String reasonCode, String reasonText) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new BusinessException(ErrorCode.DISPATCH_REJECT_REASON_REQUIRED);
        }
        OrderAssignmentEntity assignment = requireAssignment(assignmentNo);
        if (!assignment.getWorkerId().equals(workerId)) {
            throw new BusinessException(ErrorCode.DISPATCH_ASSIGNMENT_NOT_OWNED);
        }
        if (assignmentMapper.reject(assignment.getId(), LocalDateTime.now(),
                reasonCode, reasonText) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        DispatchJobEntity job = requireJobById(assignment.getDispatchJobId());
        skipCandidate(job.getId(), workerId);
        if (jobMapper.markReassigningFromPending(job.getId()) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        summarySyncer.markReassigning(assignment.getBookingId());
        dispatchToNextCandidate(job, assignment.getId(), null, "AUTO_REJECT");
        return AssignmentView.from(assignmentMapper.selectById(assignment.getId()));
    }

    // ------------------------------------------------------------------
    // 接单超时自动改派（docs/modules/08 第 8.4 节，由定时任务驱动）
    // ------------------------------------------------------------------

    /**
     * 扫描并处理接单超时的分配：EXPIRED → 释放 → REASSIGNING → 下一候选。
     *
     * <p>单单容错：一个分配的异常不阻断同批其他分配，
     * 下一轮扫描会重试仍处于 PENDING_ACCEPT 的超时记录。</p>
     *
     * @param asOf      视作当前的时刻（冒烟脚本注入模拟时钟）
     * @param batchSize 单批上限
     * @return 本轮处理的分配数
     */
    @Transactional
    public int expireTimeouts(LocalDateTime asOf, int batchSize) {
        List<OrderAssignmentEntity> expired = assignmentMapper.selectExpired(asOf, batchSize);
        int handled = 0;
        for (OrderAssignmentEntity assignment : expired) {
            try {
                if (expireOne(assignment)) {
                    handled++;
                }
            } catch (Exception exception) {
                log.warn("dispatch accept timeout handling failed, assignmentNo={}",
                        assignment.getAssignmentNo(), exception);
            }
        }
        return handled;
    }

    private boolean expireOne(OrderAssignmentEntity assignment) {
        // 条件更新兜底并发：人员恰好同时接单/拒单时本条跳过
        if (assignmentMapper.markExpired(assignment.getId()) == 0) {
            return false;
        }
        DispatchJobEntity job = jobMapper.selectById(assignment.getDispatchJobId());
        if (job == null) {
            return true;
        }
        skipCandidate(job.getId(), assignment.getWorkerId());
        // 任务已被并发拒单链路推进时不再重复改派
        if (jobMapper.markReassigningFromPending(job.getId()) == 0) {
            return true;
        }
        summarySyncer.markReassigning(assignment.getBookingId());
        dispatchToNextCandidate(job, assignment.getId(), null, "AUTO_EXPIRED");
        return true;
    }

    /**
     * 在 REASSIGNING 状态下按评分名次尝试下一候选（docs/modules/08 第 8.4 节）。
     *
     * <p>候选 hold 时段失败（被占）只跳过该候选；先锁新时段再释放旧时段，
     * 保证任何失败路径下订单时段都不悬空。全部候选不可用 → MANUAL_REQUIRED。</p>
     *
     * @param job                 派单任务（当前状态 REASSIGNING）
     * @param currentAssignmentId 被替换的分配记录 ID
     * @param operatorAccountId   运营账号 ID（自动改传 null）
     * @param reason              派单原因（自动改派写触发码）
     */
    private void dispatchToNextCandidate(DispatchJobEntity job, Long currentAssignmentId,
                                         Long operatorAccountId, String reason) {
        WorkerSlotEntity oldSlot = slotReservation.findByBooking(job.getBookingId());
        if (oldSlot == null) {
            transferToManual(job, "原分配时段不存在，需人工核实");
            return;
        }
        DispatchPolicyEntity policy = policyMapper.selectById(job.getPolicyId());
        int timeoutMinutes = policy != null ? policy.getAcceptTimeoutMinutes() : 10;
        for (DispatchCandidateEntity candidate : candidateMapper.selectByJobId(job.getId())) {
            if (!"GENERATED".equals(candidate.getStatus())) {
                continue;
            }
            WorkerSlotEntity newSlot;
            try {
                newSlot = slotReservation.hold(candidate.getWorkerId(),
                        oldSlot.getServiceDate(), oldSlot.getWindowId(), job.getBookingId());
            } catch (BusinessException exception) {
                // 该候选时段被占：仅跳过，不影响其他候选
                candidateMapper.markSkipped(candidate.getId());
                continue;
            }
            appointWorker(job, candidate, candidate.getWorkerId(), "REASSIGN",
                    currentAssignmentId, operatorAccountId, reason,
                    timeoutMinutes, oldSlot, newSlot);
            return;
        }
        transferToManual(job, "候选时段均不可用或已用尽");
    }

    /** 无候选可派：任务与订单摘要转 MANUAL_REQUIRED（docs/modules/08 第 8.4 节）。 */
    private void transferToManual(DispatchJobEntity job, String reason) {
        jobMapper.markManualRequired(job.getId(), reason);
        summarySyncer.markManualRequired(job.getBookingId());
    }

    /**
     * 落实新人员（自动改派与手工派单/改派共用）：
     * 候选标 SELECTED → 释放旧时段 → 创建 PENDING_ACCEPT 新分配 →
     * 任务 REASSIGNING → PENDING_ACCEPT → 订单摘要回 PENDING_ACCEPT。
     *
     * <p>调用方必须已把任务推进到 REASSIGNING 且 hold 到新时段，
     * markPendingAccept 条件更新 0 行时抛异常整体回滚。</p>
     */
    private void appointWorker(DispatchJobEntity job, DispatchCandidateEntity candidate,
                               long workerId, String assignType, Long replacedAssignmentId,
                               Long operatorAccountId, String reason, int timeoutMinutes,
                               WorkerSlotEntity oldSlot, WorkerSlotEntity newSlot) {
        if (candidate != null) {
            candidateMapper.markSelected(candidate.getId());
        }
        slotReservation.releaseBooked(oldSlot.getId(), job.getBookingId());
        LocalDateTime now = LocalDateTime.now();
        OrderAssignmentEntity replacement = insertAssignment(job.getId(), job.getBookingId(),
                workerId, assignType, replacedAssignmentId, operatorAccountId, now,
                now.plusMinutes(timeoutMinutes), "PENDING_ACCEPT", reason);
        if (jobMapper.markPendingAccept(job.getId(), replacement.getId(), now) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        summarySyncer.markPendingAccept(job.getBookingId(), workerId, newSlot.getId());
    }

    // ------------------------------------------------------------------
    // 运营手工派单 / 改派（docs/modules/08 第 4.3、9 节）
    // ------------------------------------------------------------------

    /**
     * 运营手工派单：MANUAL_REQUIRED 任务指定人员重新进入待接单。
     *
     * @param jobNo     任务单号
     * @param workerId  指定人员 ID
     * @param reasonText 操作原因（必填，写分配记录审计）
     * @param accountId 运营账号 ID
     * @return 任务视图
     * @throws BusinessException 任务状态不允许 / 时段不可用
     */
    @Transactional
    public DispatchJobView assignManual(String jobNo, long workerId,
                                        String reasonText, long accountId) {
        DispatchJobEntity job = requireJobByNo(jobNo);
        if (!"MANUAL_REQUIRED".equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        if (jobMapper.markReassigningFromManual(job.getId()) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        appointDesignatedWorker(job, workerId, "MANUAL", null, reasonText, accountId);
        return jobView(requireJobById(job.getId()));
    }

    /**
     * 运营手工改派：PENDING_ACCEPT / ACCEPTED 任务更换人员。
     *
     * <p>自动候选均不可用或客户要求更换时使用；旧分配（含已接单）置 REASSIGNED，
     * 新分配重新进入待接单，订单不取消、支付不变。</p>
     *
     * @param jobNo     任务单号
     * @param workerId  新人员 ID
     * @param reasonText 改派原因（必填）
     * @param accountId 运营账号 ID
     * @return 任务视图
     * @throws BusinessException 任务状态不允许 / 时段不可用
     */
    @Transactional
    public DispatchJobView reassignManual(String jobNo, long workerId,
                                          String reasonText, long accountId) {
        DispatchJobEntity job = requireJobByNo(jobNo);
        if (!"PENDING_ACCEPT".equals(job.getStatus()) && !"ACCEPTED".equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        OrderAssignmentEntity current = assignmentMapper.selectLatestByBookingId(job.getBookingId());
        boolean fromAccepted = current != null && "ACCEPTED".equals(current.getStatus());
        if (!fromAccepted && (current == null
                || !"PENDING_ACCEPT".equals(current.getStatus()))) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        int updated = fromAccepted
                ? assignmentMapper.reassignFromAccepted(current.getId())
                : assignmentMapper.reassignFromPending(current.getId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        if (fromAccepted
                ? jobMapper.markReassigningFromAccepted(job.getId()) == 0
                : jobMapper.markReassigningFromPending(job.getId()) == 0) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT);
        }
        skipCandidate(job.getId(), current.getWorkerId());
        appointDesignatedWorker(job, workerId, "MANUAL", current.getId(), reasonText, accountId);
        return jobView(requireJobById(job.getId()));
    }

    /**
     * 手工指定人员的落地：hold 时段失败直接抛给运营（不转 MANUAL_REQUIRED），
     * 候选不存在时按当前策略补一条评分快照。
     */
    private void appointDesignatedWorker(DispatchJobEntity job, long workerId,
                                         String assignType, Long replacedAssignmentId,
                                         String reasonText, long accountId) {
        WorkerSlotEntity oldSlot = slotReservation.findByBooking(job.getBookingId());
        if (oldSlot == null) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_FOUND);
        }
        WorkerSlotEntity newSlot;
        try {
            newSlot = slotReservation.hold(workerId, oldSlot.getServiceDate(),
                    oldSlot.getWindowId(), job.getBookingId());
        } catch (BusinessException exception) {
            // 手工操作直接让运营看到时段不可用，整体回滚（任务保持 MANUAL_REQUIRED）
            throw exception;
        }
        DispatchPolicyEntity policy = policyMapper.selectById(job.getPolicyId());
        DispatchCandidateEntity candidate =
                candidateMapper.selectByJobAndWorker(job.getId(), workerId);
        if (candidate == null) {
            candidate = insertCandidate(job, policy, workerId);
        }
        appointWorker(job, candidate, workerId, assignType, replacedAssignmentId,
                accountId, reasonText, policy != null ? policy.getAcceptTimeoutMinutes() : 10,
                oldSlot, newSlot);
    }

    /** 手工指定人员不在候选列表时补录评分快照（名次排在已有候选之后）。 */
    private DispatchCandidateEntity insertCandidate(DispatchJobEntity job,
                                                    DispatchPolicyEntity policy,
                                                    long workerId) {
        DispatchScorePolicy.ScoreResult result = scoreOne(
                policy != null ? policy : requireActivePolicy(), workerId);
        DispatchCandidateEntity candidate = new DispatchCandidateEntity();
        candidate.setDispatchJobId(job.getId());
        candidate.setPolicyId(job.getPolicyId());
        candidate.setWorkerId(workerId);
        candidate.setRankNo(candidateMapper.selectMaxRank(job.getId()) + 1);
        candidate.setTotalScore(result.totalScore());
        candidate.setScoreJson(toScoreJson(result));
        candidate.setStatus("GENERATED");
        candidate.setGeneratedAt(LocalDateTime.now());
        candidateMapper.insert(candidate);
        return candidate;
    }

    // ------------------------------------------------------------------
    // 查询：人员视角 / 运营视角
    // ------------------------------------------------------------------

    /**
     * 人员视角分页列表（我的派单，时间倒序）。
     *
     * @param workerId 人员 ID
     * @param page     页码（1 起）
     * @param size     每页大小
     * @return 分配视图列表
     */
    @Transactional(readOnly = true)
    public List<AssignmentView> workerAssignments(long workerId, int page, int size) {
        return assignmentMapper
                .selectPageByWorker(workerId, Math.max(0, page - 1) * size, size)
                .stream().map(AssignmentView::from).toList();
    }

    /**
     * 人员视角总数。
     *
     * @param workerId 人员 ID
     * @return 总数
     */
    @Transactional(readOnly = true)
    public long workerAssignmentCount(long workerId) {
        return assignmentMapper.countByWorker(workerId);
    }

    /**
     * 人员视角派单详情（仅本人可见）。
     *
     * @param workerId     人员 ID
     * @param assignmentNo 分配单号
     * @return 分配视图
     */
    @Transactional(readOnly = true)
    public AssignmentView workerAssignment(long workerId, String assignmentNo) {
        OrderAssignmentEntity assignment = requireAssignment(assignmentNo);
        if (!assignment.getWorkerId().equals(workerId)) {
            throw new BusinessException(ErrorCode.DISPATCH_ASSIGNMENT_NOT_OWNED);
        }
        return AssignmentView.from(assignment);
    }

    /**
     * 运营任务队列分页。
     *
     * @param status 状态过滤，可空
     * @param page   页码（1 起）
     * @param size   每页大小
     * @return 任务列表
     */
    @Transactional(readOnly = true)
    public List<DispatchJobEntity> operatorJobs(String status, int page, int size) {
        return jobMapper.selectPage(status, Math.max(0, page - 1) * size, size);
    }

    /**
     * 运营任务队列总数。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Transactional(readOnly = true)
    public long operatorJobCount(String status) {
        return jobMapper.count(status);
    }

    /**
     * 任务详情：任务 + 候选评分快照 + 分配历史链。
     *
     * @param jobNo 任务单号
     * @return 任务视图
     */
    @Transactional(readOnly = true)
    public DispatchJobView jobDetail(String jobNo) {
        return jobView(requireJobByNo(jobNo));
    }

    /**
     * 按订单查询任务详情（订单侧展示派单信息复用）。
     *
     * @param bookingId 订单 ID
     * @return 任务视图或 null
     */
    @Transactional(readOnly = true)
    public DispatchJobView jobDetailByBookingId(long bookingId) {
        DispatchJobEntity job = jobMapper.selectByBookingId(bookingId);
        return job == null ? null : jobView(job);
    }

    private DispatchJobView jobView(DispatchJobEntity job) {
        List<DispatchCandidateView> candidates = candidateMapper.selectByJobId(job.getId())
                .stream().map(DispatchCandidateView::from).toList();
        List<AssignmentView> assignments = assignmentMapper.selectByBookingId(job.getBookingId())
                .stream().map(AssignmentView::from).toList();
        return DispatchJobView.from(job, candidates, assignments);
    }

    // ------------------------------------------------------------------
    // 评分（docs/modules/08 第 7 节）
    // ------------------------------------------------------------------

    /**
     * 对候选评分并按总分降序排名（稳定排序：同分保持传入顺序，
     * 即时段库存顺序，避免无意义的次序抖动）。
     */
    private List<ScoredCandidate> scoreCandidates(DispatchPolicyEntity policy,
                                                  List<Long> orderedWorkerIds) {
        DispatchScorePolicy.ScoreWeights weights = resolveWeights(policy);
        List<ScoredCandidate> scored = new ArrayList<>(orderedWorkerIds.size());
        for (Long workerId : orderedWorkerIds) {
            scored.add(new ScoredCandidate(workerId, scoreOne(policy, workerId)));
        }
        scored.sort(Comparator.comparing(
                (ScoredCandidate item) -> item.result().totalScore()).reversed());
        return scored;
    }

    /**
     * 单个候选评分：质量分走契约，其余分项取中性值——
     * 区域/技能是硬条件（候选筛选已保证），负载/连续性/偏好由后续批次接入。
     */
    private DispatchScorePolicy.ScoreResult scoreOne(DispatchPolicyEntity policy,
                                                     long workerId) {
        int quality = qualityScoreProvider.qualityScore(workerId);
        return DispatchScorePolicy.score(
                new DispatchScorePolicy.ScoreInputs(
                        NEUTRAL_SCORE, NEUTRAL_SCORE, quality,
                        NEUTRAL_SCORE, NEUTRAL_SCORE, NEUTRAL_SCORE),
                resolveWeights(policy));
    }

    /** 解析策略权重 JSON，缺失字段回退默认权重。 */
    private DispatchScorePolicy.ScoreWeights resolveWeights(DispatchPolicyEntity policy) {
        if (policy == null || policy.getWeightsJson() == null) {
            return DispatchScorePolicy.ScoreWeights.DEFAULT;
        }
        try {
            JsonNode node = objectMapper.readTree(policy.getWeightsJson());
            return new DispatchScorePolicy.ScoreWeights(
                    intOr(node, DispatchScorePolicy.KEY_AREA,
                            DispatchScorePolicy.ScoreWeights.DEFAULT.area()),
                    intOr(node, DispatchScorePolicy.KEY_WORKLOAD,
                            DispatchScorePolicy.ScoreWeights.DEFAULT.workload()),
                    intOr(node, DispatchScorePolicy.KEY_QUALITY,
                            DispatchScorePolicy.ScoreWeights.DEFAULT.quality()),
                    intOr(node, DispatchScorePolicy.KEY_SKILL,
                            DispatchScorePolicy.ScoreWeights.DEFAULT.skill()),
                    intOr(node, DispatchScorePolicy.KEY_CONTINUITY,
                            DispatchScorePolicy.ScoreWeights.DEFAULT.continuity()),
                    intOr(node, DispatchScorePolicy.KEY_PREFERENCE,
                            DispatchScorePolicy.ScoreWeights.DEFAULT.preference()));
        } catch (Exception exception) {
            log.warn("dispatch policy weights_json invalid, policyId={}, fallback to default",
                    policy.getId(), exception);
            return DispatchScorePolicy.ScoreWeights.DEFAULT;
        }
    }

    private int intOr(JsonNode node, String key, int fallback) {
        return node.hasNonNull(key) && node.get(key).isInt()
                ? node.get(key).asInt() : fallback;
    }

    /** 评分快照 JSON：分项拆解 + 总分，不含客户地址或电话（docs 第 7 节）。 */
    private String toScoreJson(DispatchScorePolicy.ScoreResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>(result.breakdown());
        snapshot.put("total_score", result.totalScore());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("候选评分 JSON 序列化失败", exception);
        }
    }

    private record ScoredCandidate(Long workerId, DispatchScorePolicy.ScoreResult result) {
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private DispatchJobEntity requireByBookingId(long bookingId) {
        DispatchJobEntity job = jobMapper.selectByBookingId(bookingId);
        if (job == null) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT, "派单任务不存在");
        }
        return job;
    }

    private DispatchJobEntity requireJobById(long jobId) {
        DispatchJobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT, "派单任务不存在");
        }
        return job;
    }

    private DispatchJobEntity requireJobByNo(String jobNo) {
        DispatchJobEntity job = jobMapper.selectByJobNo(jobNo);
        if (job == null) {
            throw new BusinessException(ErrorCode.DISPATCH_JOB_CONFLICT, "派单任务不存在");
        }
        return job;
    }

    private OrderAssignmentEntity requireAssignment(String assignmentNo) {
        OrderAssignmentEntity assignment = assignmentMapper.selectByAssignmentNo(assignmentNo);
        if (assignment == null) {
            throw new BusinessException(ErrorCode.DISPATCH_ASSIGNMENT_NOT_FOUND);
        }
        return assignment;
    }

    /** 拒单/超时/改派时把该人员的候选标 SKIPPED，避免改派再选回他。 */
    private void skipCandidate(long jobId, long workerId) {
        DispatchCandidateEntity candidate = candidateMapper.selectByJobAndWorker(jobId, workerId);
        if (candidate != null) {
            candidateMapper.markSkipped(candidate.getId());
        }
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
                                                   LocalDateTime acceptDeadline,
                                                   String status, String dispatchReason) {
        OrderAssignmentEntity assignment = new OrderAssignmentEntity();
        assignment.setAssignmentNo(noGenerator.next(SEQ_ASSIGNMENT, PREFIX_ASSIGNMENT));
        assignment.setDispatchJobId(jobId);
        assignment.setBookingId(bookingId);
        assignment.setWorkerId(workerId);
        assignment.setAssignType(assignType);
        assignment.setStatus(status);
        assignment.setDispatchReason(dispatchReason);
        assignment.setAssignedByAccountId(accountId);
        assignment.setCreatedByAccountId(accountId);
        assignment.setUpdatedByAccountId(accountId);
        assignment.setAssignedAt(assignedAt);
        assignment.setAcceptDeadline(acceptDeadline);
        assignment.setReplacedAssignmentId(replacedAssignmentId);
        assignmentMapper.insert(assignment);
        return assignment;
    }
}
