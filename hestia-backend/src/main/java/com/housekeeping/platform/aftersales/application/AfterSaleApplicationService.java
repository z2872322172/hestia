package com.housekeeping.platform.aftersales.application;

import com.housekeeping.platform.aftersales.application.view.AfterSaleTicketView;
import com.housekeeping.platform.aftersales.domain.TicketStatus;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleAttachmentEntity;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleEventEntity;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleTicketEntity;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleAttachmentMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleEventMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleTicketMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceRecordEntity;
import com.housekeeping.platform.fulfillment.persistence.mapper.ServiceRecordMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.payment.application.RefundApplicationService;
import com.housekeeping.platform.payment.persistence.entity.PaymentRecordEntity;
import com.housekeeping.platform.payment.persistence.entity.RefundRecordEntity;
import com.housekeeping.platform.payment.persistence.mapper.PaymentRecordMapper;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 售后工单应用服务（docs/modules/11 第 6、7 节）。
 *
 * <p>核心边界：工单只形成结论（resolution_type / responsibility），
 * 退款通过幂等键 {@code AFTER_SALE:&lt;ticketNo&gt;} 委托支付模块创建退款申请，
 * 由支付审批流执行——工单不直接改支付表，只回填 refund_id 引用。
 * 状态流转一律条件更新；每次变更追加时间线事件。</p>
 */
@Service
@RequiresPersistence
public class AfterSaleApplicationService {

    private static final String SEQ_TICKET = "AFTER_SALE";

    private static final String PREFIX_TICKET = "AST";

    /** 可发起售后的订单状态（docs 第 7 节：必须关联已确认订单）。 */
    private static final Set<String> ELIGIBLE_ORDER_STATUS =
            Set.of("CONFIRMED", "EN_ROUTE", "IN_SERVICE", "PENDING_CONFIRM", "COMPLETED");

    private static final Set<String> TICKET_TYPES = Set.of(
            "QUALITY_COMPLAINT", "LATE_ARRIVAL", "DAMAGE", "REWORK",
            "REFUND_REQUEST", "SERVICE_ATTITUDE", "OTHER");

    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private static final Set<String> RESOLUTION_TYPES = Set.of(
            "REFUND", "PARTIAL_REFUND", "REWORK", "COMPENSATION", "EXPLANATION", "NO_ACTION");

    private static final Set<String> RESPONSIBILITIES = Set.of(
            "UNDETERMINED", "COMPANY", "WORKER", "CUSTOMER", "SHARED", "NONE");

    private static final Set<String> ATTACHMENT_TYPES = Set.of(
            "CUSTOMER_EVIDENCE", "HANDLER_EVIDENCE", "RESOLUTION_DOCUMENT", "OTHER");

    /** 处理时限（小时），按优先级。 */
    private static final long SLA_HOURS_LOW = 72;
    private static final long SLA_HOURS_NORMAL = 24;
    private static final long SLA_HOURS_HIGH = 8;
    private static final long SLA_HOURS_URGENT = 4;

    private final AfterSaleTicketMapper ticketMapper;

    private final AfterSaleEventMapper eventMapper;

    private final AfterSaleAttachmentMapper attachmentMapper;

    private final BookingOrderMapper orderMapper;

    private final ServiceRecordMapper recordMapper;

    private final CustomerProfileService customerProfileService;

    private final RefundApplicationService refundApplicationService;

    private final PaymentRecordMapper paymentRecordMapper;

    private final WorkerQualityService workerQualityService;

    private final WorkerProfileService workerProfileService;

    private final BusinessNoGenerator noGenerator;

    public AfterSaleApplicationService(AfterSaleTicketMapper ticketMapper,
                                       AfterSaleEventMapper eventMapper,
                                       AfterSaleAttachmentMapper attachmentMapper,
                                       BookingOrderMapper orderMapper,
                                       ServiceRecordMapper recordMapper,
                                       CustomerProfileService customerProfileService,
                                       RefundApplicationService refundApplicationService,
                                       PaymentRecordMapper paymentRecordMapper,
                                       WorkerQualityService workerQualityService,
                                       WorkerProfileService workerProfileService,
                                       BusinessNoGenerator noGenerator) {
        this.ticketMapper = ticketMapper;
        this.eventMapper = eventMapper;
        this.attachmentMapper = attachmentMapper;
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.customerProfileService = customerProfileService;
        this.refundApplicationService = refundApplicationService;
        this.paymentRecordMapper = paymentRecordMapper;
        this.workerQualityService = workerQualityService;
        this.workerProfileService = workerProfileService;
        this.noGenerator = noGenerator;
    }

    // ------------------------------------------------------------------
    // 客户侧（docs/modules/11 第 4.1 节）
    // ------------------------------------------------------------------

    /**
     * 客户提交售后工单。
     *
     * @param customerAccountId 客户账号 ID
     * @param orderNo           订单号
     * @param ticketType        工单类型
     * @param description       问题描述
     * @param priority          优先级（可空默认 NORMAL）
     * @param fileIds           附件文件（可空）
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView create(long customerAccountId, String orderNo, String ticketType,
                                      String description, String priority, List<Long> fileIds) {
        BookingOrderEntity order = requireOwnedOrder(customerAccountId, orderNo);
        if (!ELIGIBLE_ORDER_STATUS.contains(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.AFTER_SALE_NOT_ALLOWED);
        }
        if (!TICKET_TYPES.contains(ticketType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单类型不合法");
        }
        String effectivePriority = priority == null ? "NORMAL" : priority;
        if (!PRIORITIES.contains(effectivePriority)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "优先级不合法");
        }

        ServiceRecordEntity record = recordMapper.selectByBookingId(order.getId());
        LocalDateTime now = LocalDateTime.now();

        AfterSaleTicketEntity ticket = new AfterSaleTicketEntity();
        ticket.setTicketNo(noGenerator.next(SEQ_TICKET, PREFIX_TICKET));
        ticket.setBookingId(order.getId());
        ticket.setServiceRecordId(record == null ? null : record.getId());
        ticket.setCustomerId(order.getCustomerId());
        ticket.setWorkerId(order.getCurrentWorkerId());
        ticket.setTicketType(ticketType);
        ticket.setDescription(description);
        ticket.setPriority(effectivePriority);
        ticket.setStatus(TicketStatus.SUBMITTED.name());
        ticket.setResponsibility("UNDETERMINED");
        ticket.setDueAt(now.plusHours(slaHours(effectivePriority)));
        ticket.setCreatedByAccountId(customerAccountId);
        ticketMapper.insert(ticket);

        appendEvent(ticket, "CREATED", null, TicketStatus.SUBMITTED.name(),
                "客户提交售后工单", customerAccountId, "CUSTOMER");

        if (fileIds != null && !fileIds.isEmpty()) {
            for (Long fileId : fileIds) {
                AfterSaleAttachmentEntity existing = attachmentMapper.selectByFileId(fileId);
                if (existing != null) {
                    throw new BusinessException(ErrorCode.AFTER_SALE_ATTACHMENT_INVALID,
                            "文件已被其他工单使用");
                }
                AfterSaleAttachmentEntity attachment = new AfterSaleAttachmentEntity();
                attachment.setTicketId(ticket.getId());
                attachment.setFileId(fileId);
                attachment.setAttachmentType("CUSTOMER_EVIDENCE");
                attachment.setUploadedByAccountId(customerAccountId);
                attachmentMapper.insert(attachment);
            }
        }

        syncOrderAfterSaleStatus(order.getId(), "PROCESSING");
        return detailView(ticket);
    }

    /**
     * 客户工单列表。
     *
     * @param customerAccountId 客户账号 ID
     * @param page              页码
     * @param size              条数
     * @return 分页工单
     */
    @Transactional(readOnly = true)
    public PageResult<AfterSaleTicketView> myTickets(long customerAccountId, int page, int size) {
        long customerId = customerProfileService.requireUsable(customerAccountId).customerId();
        List<AfterSaleTicketEntity> tickets =
                ticketMapper.selectPageByCustomer(customerId, (page - 1) * size, size);
        long total = ticketMapper.countByCustomer(customerId);
        return PageResult.of(tickets.stream().map(this::detailView).toList(), page, size, total);
    }

    /**
     * 客户查看工单详情（归属校验）。
     *
     * @param customerAccountId 客户账号 ID
     * @param ticketNo          工单号
     * @return 工单视图
     */
    @Transactional(readOnly = true)
    public AfterSaleTicketView customerTicket(long customerAccountId, String ticketNo) {
        return detailView(requireOwnedTicket(customerAccountId, ticketNo));
    }

    /**
     * 客户补充回复：PENDING_CUSTOMER → PROCESSING（不同意当前结论，继续处理）。
     *
     * @param customerAccountId 客户账号 ID
     * @param ticketNo          工单号
     * @param message           回复内容
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView customerReply(long customerAccountId, String ticketNo,
                                             String message) {
        AfterSaleTicketEntity ticket = requireOwnedTicket(customerAccountId, ticketNo);
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        if (from != TicketStatus.PENDING_CUSTOMER) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        requireTransition(from, TicketStatus.PROCESSING);
        if (ticketMapper.backToProcessing(ticketNo, from.name()) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "CUSTOMER_REPLY", from.name(),
                TicketStatus.PROCESSING.name(), message, customerAccountId, "CUSTOMER");
        return detailView(reload(ticket));
    }

    /**
     * 客户确认处理方案：PENDING_CUSTOMER → CLOSED。
     *
     * @param customerAccountId 客户账号 ID
     * @param ticketNo          工单号
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView acceptResolution(long customerAccountId, String ticketNo) {
        AfterSaleTicketEntity ticket = requireOwnedTicket(customerAccountId, ticketNo);
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        if (ticketMapper.acceptByCustomer(ticketNo) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "CLOSED", from.name(), TicketStatus.CLOSED.name(),
                "客户确认处理方案", customerAccountId, "CUSTOMER");
        syncOrderAfterSaleStatus(ticket.getBookingId(), "CLOSED");
        return detailView(reload(ticket));
    }

    // ------------------------------------------------------------------
    // 人员侧（docs/modules/11 第 4.2 节）
    // ------------------------------------------------------------------

    /**
     * 人员工单列表（与本人相关的工单）。
     *
     * @param accountId 人员账号 ID
     * @param page      页码
     * @param size      条数
     * @return 分页工单
     */
    @Transactional(readOnly = true)
    public PageResult<AfterSaleTicketView> workerTickets(long accountId, int page, int size) {
        long workerId = workerProfileService.requireByAccountId(accountId).getId();
        List<AfterSaleTicketEntity> tickets =
                ticketMapper.selectPageByWorker(workerId, (page - 1) * size, size);
        long total = ticketMapper.countByWorker(workerId);
        return PageResult.of(tickets.stream().map(this::detailView).toList(), page, size, total);
    }

    /**
     * 人员陈述说明：PENDING_WORKER → PROCESSING。
     *
     * @param workerId 人员 ID
     * @param ticketNo 工单号
     * @param message  陈述内容
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView workerStatement(long workerId, String ticketNo, String message) {
        AfterSaleTicketEntity ticket = requireWorkerTicket(workerId, ticketNo);
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        if (from != TicketStatus.PENDING_WORKER) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        requireTransition(from, TicketStatus.PROCESSING);
        if (ticketMapper.backToProcessing(ticketNo, from.name()) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "WORKER_STATEMENT", from.name(),
                TicketStatus.PROCESSING.name(), message, null, "WORKER");
        return detailView(reload(ticket));
    }

    // ------------------------------------------------------------------
    // 运营侧（docs/modules/11 第 4.3 节）
    // ------------------------------------------------------------------

    /**
     * 运营工单分页。
     *
     * @param status     状态过滤（可空）
     * @param ticketType 类型过滤（可空）
     * @param page       页码
     * @param size       条数
     * @return 分页工单
     */
    @Transactional(readOnly = true)
    public PageResult<AfterSaleTicketView> operatorPage(String status, String ticketType,
                                                        int page, int size) {
        List<AfterSaleTicketEntity> tickets =
                ticketMapper.selectPage(status, ticketType, (page - 1) * size, size);
        long total = ticketMapper.count(status, ticketType);
        return PageResult.of(tickets.stream().map(this::detailView).toList(), page, size, total);
    }

    /**
     * 运营查看工单详情。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @Transactional(readOnly = true)
    public AfterSaleTicketView operatorDetail(String ticketNo) {
        return detailView(requireTicket(ticketNo));
    }

    /**
     * 运营受理：SUBMITTED → PROCESSING。
     *
     * @param ticketNo          工单号
     * @param operatorAccountId 受理运营账号 ID
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView accept(String ticketNo, long operatorAccountId) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        if (ticketMapper.accept(ticketNo, operatorAccountId) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "ACCEPTED", TicketStatus.SUBMITTED.name(),
                TicketStatus.PROCESSING.name(), "运营受理工单", operatorAccountId, "OPERATOR");
        return detailView(reload(ticket));
    }

    /**
     * 运营转挂起：PROCESSING → PENDING_*。
     *
     * @param ticketNo          工单号
     * @param pendingStatus     目标挂起状态
     * @param reason            原因说明
     * @param operatorAccountId 运营账号 ID
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView toPending(String ticketNo, String pendingStatus, String reason,
                                         long operatorAccountId) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        TicketStatus to = parseStatus(pendingStatus);
        if (!to.isPendingFeedback()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "目标状态不是待反馈状态");
        }
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        requireTransition(from, to);
        if (ticketMapper.toPending(ticketNo, to.name()) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), to.name(), from.name(), to.name(), reason,
                operatorAccountId, "OPERATOR");
        return detailView(reload(ticket));
    }

    /**
     * 运营恢复处理：PENDING_* → PROCESSING。
     *
     * @param ticketNo          工单号
     * @param operatorAccountId 运营账号 ID
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView backToProcessing(String ticketNo, long operatorAccountId) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        requireTransition(from, TicketStatus.PROCESSING);
        if (ticketMapper.backToProcessing(ticketNo, from.name()) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "BACK_PROCESSING", from.name(),
                TicketStatus.PROCESSING.name(), "恢复处理", operatorAccountId, "OPERATOR");
        return detailView(reload(ticket));
    }

    /**
     * 运营形成结论：活动状态 → RESOLVED（docs/modules/11 第 7 节）。
     *
     * <p>结论为 REFUND / PARTIAL_REFUND 时，以幂等键 {@code AFTER_SALE:&lt;ticketNo&gt;}
     * 创建退款申请（全额剩余 / 指定金额），回填 refund_id；退款执行走支付审批流。</p>
     *
     * @param ticketNo          工单号
     * @param resolutionType    处理结果
     * @param resolutionText    结论说明（必填）
     * @param responsibility    责任认定
     * @param refundAmount      部分退款金额（PARTIAL_REFUND 必填）
     * @param operatorAccountId 运营账号 ID
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView resolve(String ticketNo, String resolutionType,
                                       String resolutionText, String responsibility,
                                       BigDecimal refundAmount, long operatorAccountId) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        if (resolutionText == null || resolutionText.isBlank()) {
            throw new BusinessException(ErrorCode.AFTER_SALE_RESOLUTION_REQUIRED);
        }
        if (!RESOLUTION_TYPES.contains(resolutionType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "处理结果类型不合法");
        }
        String effectiveResponsibility =
                responsibility == null ? "UNDETERMINED" : responsibility;
        if (!RESPONSIBILITIES.contains(effectiveResponsibility)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "责任认定不合法");
        }

        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        requireTransition(from, TicketStatus.RESOLVED);
        if (ticketMapper.resolve(ticketNo, resolutionType, resolutionText,
                effectiveResponsibility) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }

        if ("REFUND".equals(resolutionType) || "PARTIAL_REFUND".equals(resolutionType)) {
            linkRefund(reload(ticket), resolutionType, refundAmount, resolutionText,
                    operatorAccountId);
        }

        AfterSaleTicketEntity resolved = reload(ticket);
        appendEvent(resolved, "RESOLVED", from.name(), TicketStatus.RESOLVED.name(),
                resolutionText, operatorAccountId, "OPERATOR");
        syncOrderAfterSaleStatus(ticket.getBookingId(), "RESOLVED");
        if (ticket.getWorkerId() != null) {
            workerQualityService.recalculate(ticket.getWorkerId());
        }
        return detailView(resolved);
    }

    /**
     * 运营驳回工单：活动状态 → REJECTED（投诉不成立）。
     *
     * @param ticketNo          工单号
     * @param reason            驳回理由
     * @param operatorAccountId 运营账号 ID
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView reject(String ticketNo, String reason, long operatorAccountId) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        requireTransition(from, TicketStatus.REJECTED);
        if (ticketMapper.reject(ticketNo, reason) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "REJECTED", from.name(), TicketStatus.REJECTED.name(),
                reason, operatorAccountId, "OPERATOR");
        // REJECTED 属处理完毕，订单售后摘要收敛到 RESOLVED（CHECK 仅四个取值）
        syncOrderAfterSaleStatus(ticket.getBookingId(), "RESOLVED");
        if (ticket.getWorkerId() != null) {
            workerQualityService.recalculate(ticket.getWorkerId());
        }
        return detailView(reload(ticket));
    }

    /**
     * 运营关闭工单：RESOLVED / REJECTED → CLOSED。
     *
     * @param ticketNo          工单号
     * @param operatorAccountId 运营账号 ID
     * @return 工单视图
     */
    @Transactional
    public AfterSaleTicketView close(String ticketNo, long operatorAccountId) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        TicketStatus from = TicketStatus.valueOf(ticket.getStatus());
        requireTransition(from, TicketStatus.CLOSED);
        if (ticketMapper.close(ticketNo) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        appendEvent(reload(ticket), "CLOSED", from.name(), TicketStatus.CLOSED.name(),
                "运营关闭工单", operatorAccountId, "OPERATOR");
        syncOrderAfterSaleStatus(ticket.getBookingId(), "CLOSED");
        return detailView(reload(ticket));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 委托支付模块创建退款申请并回填 refund_id（docs 第 1 节核心边界）。
     */
    private void linkRefund(AfterSaleTicketEntity ticket, String resolutionType,
                            BigDecimal refundAmount, String resolutionText,
                            long operatorAccountId) {
        PaymentRecordEntity payment =
                paymentRecordMapper.selectSuccessByBookingId(ticket.getBookingId());
        if (payment == null) {
            // 无成功支付就没有可退的钱，结论不能挂空引用
            throw new BusinessException(ErrorCode.AFTER_SALE_REFUND_LINK_REQUIRED);
        }
        BigDecimal amount = "REFUND".equals(resolutionType)
                ? refundApplicationService.refundableAmount(payment.getId())
                : refundAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "退款金额必须大于 0");
        }
        RefundRecordEntity refund = refundApplicationService.createRequest(
                payment.getId(), amount, "AFTER_SALE", resolutionText, operatorAccountId,
                "AFTER_SALE:" + ticket.getTicketNo());
        if (ticketMapper.linkRefund(ticket.getTicketNo(), refund.getId()) == 0) {
            throw new BusinessException(ErrorCode.AFTER_SALE_REFUND_LINK_REQUIRED,
                    "工单已绑定退款记录");
        }
        appendEvent(ticket, "REFUND_LINKED", ticket.getStatus(), ticket.getStatus(),
                "已创建退款申请：" + refund.getRefundNo(), operatorAccountId, "OPERATOR");
    }

    private long slaHours(String priority) {
        return switch (priority) {
            case "URGENT" -> SLA_HOURS_URGENT;
            case "HIGH" -> SLA_HOURS_HIGH;
            case "LOW" -> SLA_HOURS_LOW;
            default -> SLA_HOURS_NORMAL;
        };
    }

    private void requireTransition(TicketStatus from, TicketStatus to) {
        if (!from.canTransitTo(to)) {
            throw new BusinessException(ErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
    }

    private TicketStatus parseStatus(String status) {
        try {
            return TicketStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单状态不合法");
        }
    }

    /** 时间线只增不改。 */
    private void appendEvent(AfterSaleTicketEntity ticket, String eventType, String fromStatus,
                             String toStatus, String description, Long operatorAccountId,
                             String operatorRole) {
        AfterSaleEventEntity event = new AfterSaleEventEntity();
        event.setTicketId(ticket.getId());
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setDescription(description);
        event.setOperatorAccountId(operatorAccountId);
        event.setOperatorRole(operatorRole);
        eventMapper.insert(event);
    }

    /** 订单售后摘要同步（NONE → PROCESSING → RESOLVED → CLOSED）。 */
    private void syncOrderAfterSaleStatus(long bookingId, String status) {
        BookingOrderEntity patch = new BookingOrderEntity();
        patch.setId(bookingId);
        patch.setAfterSaleStatus(status);
        orderMapper.updateById(patch);
    }

    private AfterSaleTicketEntity requireTicket(String ticketNo) {
        AfterSaleTicketEntity ticket = ticketMapper.selectByTicketNo(ticketNo);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.AFTER_SALE_NOT_FOUND);
        }
        return ticket;
    }

    /** 校验订单归属（越权一律 404，不泄露存在性）。 */
    private BookingOrderEntity requireOwnedOrder(long customerAccountId, String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (customerProfileService.requireUsable(customerAccountId).customerId()
                != order.getCustomerId()) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        return order;
    }

    private AfterSaleTicketEntity requireOwnedTicket(long customerAccountId, String ticketNo) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        if (customerProfileService.requireUsable(customerAccountId).customerId()
                != ticket.getCustomerId()) {
            // 越权与不存在同样 404，防探测
            throw new BusinessException(ErrorCode.AFTER_SALE_NOT_OWNED);
        }
        return ticket;
    }

    private AfterSaleTicketEntity requireWorkerTicket(long workerId, String ticketNo) {
        AfterSaleTicketEntity ticket = requireTicket(ticketNo);
        if (ticket.getWorkerId() == null || ticket.getWorkerId() != workerId) {
            throw new BusinessException(ErrorCode.AFTER_SALE_NOT_OWNED);
        }
        return ticket;
    }

    private AfterSaleTicketEntity reload(AfterSaleTicketEntity stale) {
        return ticketMapper.selectById(stale.getId());
    }

    private AfterSaleTicketView detailView(AfterSaleTicketEntity ticket) {
        return AfterSaleTicketView.from(ticket,
                eventMapper.selectByTicketId(ticket.getId()),
                attachmentMapper.selectByTicketId(ticket.getId()));
    }
}
