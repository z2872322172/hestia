package com.housekeeping.platform.payment.application;

import com.housekeeping.platform.booking.application.BookingStatusCommandService;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.payment.application.view.RefundView;
import com.housekeeping.platform.payment.domain.PaymentGateway;
import com.housekeeping.platform.payment.domain.PaymentStatus;
import com.housekeeping.platform.payment.domain.RefundStatus;
import com.housekeeping.platform.payment.persistence.entity.PaymentRecordEntity;
import com.housekeeping.platform.payment.persistence.entity.RefundRecordEntity;
import com.housekeeping.platform.payment.persistence.mapper.PaymentRecordMapper;
import com.housekeeping.platform.payment.persistence.mapper.RefundRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款应用服务（docs/modules/10 第 8 节）。
 *
 * <p>退款申请、审批与执行分开：取消已支付订单自动生成 REQUESTED 申请，
 * 运营 approve 时在同一事务内执行网关退款（Mock 渠道同步成功），
 * 成功后调用订单内部命令累加退款摘要。外部退款失败如实置 FAILED 并保留原因，
 * 允许运营 retry（docs 第 2.12 条：外部退款失败不能伪装成成功）。</p>
 */
@Service
@RequiresPersistence
public class RefundApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RefundApplicationService.class);

    private static final String SEQ_REFUND = "REFUND";

    private static final String PREFIX_REFUND = "RFD";

    private final RefundRecordMapper refundMapper;

    private final PaymentRecordMapper paymentMapper;

    private final BookingStatusCommandService bookingStatusCommandService;

    private final PaymentGateway gateway;

    private final BusinessNoGenerator noGenerator;

    public RefundApplicationService(RefundRecordMapper refundMapper,
                                    PaymentRecordMapper paymentMapper,
                                    BookingStatusCommandService bookingStatusCommandService,
                                    PaymentGateway gateway,
                                    BusinessNoGenerator noGenerator) {
        this.refundMapper = refundMapper;
        this.paymentMapper = paymentMapper;
        this.bookingStatusCommandService = bookingStatusCommandService;
        this.gateway = gateway;
        this.noGenerator = noGenerator;
    }

    /**
     * 取消已支付订单时生成退款申请（docs/modules/10 第 8.1 节来源一）。
     *
     * <p>幂等键 {@code BOOKING_CANCEL:<bookingId>}——取消记录唯一，
     * 同一订单的取消重放不会生成第二笔申请。</p>
     *
     * @param bookingId            订单 ID
     * @param amount               应退金额（来自取消费用计算）
     * @param reasonCode           原因编码
     * @param reasonText           原因描述
     * @param requestedByAccountId 申请人账号 ID
     * @return 退款记录
     */
    @Transactional
    public RefundRecordEntity createForBookingCancellation(long bookingId, BigDecimal amount,
                                                           String reasonCode, String reasonText,
                                                           Long requestedByAccountId) {
        PaymentRecordEntity payment = paymentMapper.selectSuccessByBookingId(bookingId);
        if (payment == null) {
            // 没有成功支付就没有可退的钱，静默跳过（未支付取消无退款）
            return null;
        }
        String idempotencyKey = "BOOKING_CANCEL:" + bookingId;
        RefundRecordEntity existing =
                refundMapper.selectByIdempotencyKey(payment.getId(), idempotencyKey);
        if (existing != null) {
            return existing;
        }
        return insertRefund(payment, amount, reasonCode, reasonText,
                requestedByAccountId, idempotencyKey);
    }

    /**
     * 客户对已支付订单直接发起退款申请（docs/modules/10 第 15.1 节）。
     *
     * <p>金额为"剩余可退全额"；订单是否允许退款（如服务已开始转售后）由订单状态兜底——
     * 已取消订单的成功支付仍可退（取消时已生成申请，幂等键防重）。</p>
     *
     * @param paymentId      成功支付记录 ID
     * @param amount         退款金额
     * @param reasonCode     原因编码
     * @param reasonText     原因描述
     * @param requestedByAccountId 申请人账号 ID
     * @param idempotencyKey 业务幂等键
     * @return 退款记录
     */
    @Transactional
    public RefundRecordEntity createRequest(long paymentId, BigDecimal amount,
                                            String reasonCode, String reasonText,
                                            Long requestedByAccountId, String idempotencyKey) {
        PaymentRecordEntity payment = paymentMapper.selectById(paymentId);
        if (payment == null || !PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }
        RefundRecordEntity existing =
                refundMapper.selectByIdempotencyKey(paymentId, idempotencyKey);
        if (existing != null) {
            return existing;
        }
        return insertRefund(payment, amount, reasonCode, reasonText,
                requestedByAccountId, idempotencyKey);
    }

    /**
     * 剩余可退金额（成功支付金额 - 已成功退款金额）。
     *
     * @param paymentId 支付记录 ID
     * @return 剩余可退金额
     */
    @Transactional(readOnly = true)
    public BigDecimal refundableAmount(long paymentId) {
        PaymentRecordEntity payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment.getAmount().subtract(refundMapper.sumSuccessAmount(paymentId));
    }

    /**
     * 审批通过并执行退款（docs/modules/10 第 8.2、8.3 节）。
     *
     * <p>同一事务：REQUESTED → APPROVED → PROCESSING → 网关 → SUCCESS →
     * 订单退款摘要。网关失败置 FAILED 保留原因并抛 {@code REFUND_GATEWAY_ERROR}，
     * 事务回滚会把 PROCESSING 回退——因此失败路径在独立事务中落 FAILED
     * 由 {@link #retry} 重新走 PROCESSING；Mock 渠道同步成功不触发该分支。</p>
     *
     * @param refundNo           退款单号
     * @param reviewedByAccountId 审核人账号 ID
     * @return 退款详情
     */
    @Transactional
    public RefundView approveAndExecute(String refundNo, long reviewedByAccountId) {
        RefundRecordEntity refund = requireByRefundNo(refundNo);
        if (refundMapper.approve(refundNo, reviewedByAccountId) == 0) {
            throw new BusinessException(ErrorCode.REFUND_STATUS_CONFLICT);
        }
        return execute(refundNo);
    }

    /**
     * 拒绝退款申请（REQUESTED → REJECTED，写审核人与原因）。
     *
     * <p>若订单支付摘要处于 REFUNDING 且尚无成功退款，回退为 PAID。</p>
     *
     * @param refundNo            退款单号
     * @param reviewedByAccountId 审核人账号 ID
     * @param reasonText          拒绝原因
     * @return 退款详情
     */
    @Transactional
    public RefundView reject(String refundNo, long reviewedByAccountId, String reasonText) {
        requireByRefundNo(refundNo);
        if (refundMapper.reject(refundNo, reviewedByAccountId, reasonText) == 0) {
            throw new BusinessException(ErrorCode.REFUND_STATUS_CONFLICT);
        }
        RefundRecordEntity refund = requireByRefundNo(refundNo);
        bookingStatusCommandService.revertRefunding(refund.getBookingId());
        return RefundView.from(refund);
    }

    /**
     * 重试失败的退款（FAILED → PROCESSING → 网关）。
     *
     * @param refundNo 退款单号
     * @return 退款详情
     */
    @Transactional
    public RefundView retry(String refundNo) {
        requireByRefundNo(refundNo);
        return execute(refundNo);
    }

    /**
     * 执行退款：置 PROCESSING → 调网关 → SUCCESS + 订单摘要。
     */
    private RefundView execute(String refundNo) {
        if (refundMapper.markProcessing(refundNo) == 0) {
            throw new BusinessException(ErrorCode.REFUND_STATUS_CONFLICT);
        }
        RefundRecordEntity refund = requireByRefundNo(refundNo);
        PaymentRecordEntity payment = paymentMapper.selectById(refund.getPaymentId());
        PaymentGateway.GatewayRefundResult result = gateway.refund(
                payment != null ? payment.getPaymentNo() : "-", refundNo, refund.getAmount());
        if (!result.success()) {
            // 外部失败如实落库：先记 FAILED 再抛，让调用方看到真实渠道错误
            refundMapper.markFailed(refundNo, result.errorMessage());
            log.warn("refund gateway failed, refundNo={}, reason={}", refundNo, result.errorMessage());
            throw new BusinessException(ErrorCode.REFUND_GATEWAY_ERROR, result.errorMessage());
        }
        if (refundMapper.markSuccess(refundNo, LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.REFUND_STATUS_CONFLICT);
        }
        bookingStatusCommandService.applyRefund(refund.getBookingId(), refund.getAmount());
        return RefundView.from(requireByRefundNo(refundNo));
    }

    /**
     * 客户查询自己的退款单。
     *
     * @param accountId 客户账号 ID
     * @param refundNo  退款单号
     * @return 退款详情
     */
    @Transactional(readOnly = true)
    public RefundView customerRefund(long accountId, String refundNo) {
        RefundRecordEntity refund = requireByRefundNo(refundNo);
        if (refund.getRequestedByAccountId() == null
                || refund.getRequestedByAccountId() != accountId) {
            throw new BusinessException(ErrorCode.REFUND_NOT_FOUND);
        }
        return RefundView.from(refund);
    }

    /**
     * 订单退款流水（客户侧，经订单归属校验后调用）。
     *
     * @param bookingId 订单 ID
     * @return 退款列表
     */
    @Transactional(readOnly = true)
    public List<RefundView> listByBooking(long bookingId) {
        return refundMapper.selectByBookingId(bookingId).stream().map(RefundView::from).toList();
    }

    /**
     * 运营退款列表。
     *
     * @param page   页码
     * @param size   每页大小
     * @param status 状态过滤，可空
     * @return 退款列表
     */
    @Transactional(readOnly = true)
    public List<RefundView> operatorList(int page, int size, String status) {
        return refundMapper.selectPage(status, (long) (page - 1) * size, size)
                .stream().map(RefundView::from).toList();
    }

    /**
     * 运营退款总数。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Transactional(readOnly = true)
    public long operatorCount(String status) {
        return refundMapper.count(status);
    }

    private RefundRecordEntity insertRefund(PaymentRecordEntity payment, BigDecimal amount,
                                            String reasonCode, String reasonText,
                                            Long requestedByAccountId, String idempotencyKey) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }
        BigDecimal refundable = payment.getAmount().subtract(
                refundMapper.sumSuccessAmount(payment.getId()));
        if (amount.compareTo(refundable) > 0) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }
        RefundRecordEntity refund = new RefundRecordEntity();
        refund.setRefundNo(noGenerator.next(SEQ_REFUND, PREFIX_REFUND));
        refund.setBookingId(payment.getBookingId());
        refund.setPaymentId(payment.getId());
        refund.setAmount(amount);
        refund.setReasonCode(reasonCode == null || reasonCode.isBlank() ? "OTHER" : reasonCode);
        refund.setReasonText(reasonText);
        refund.setStatus(RefundStatus.REQUESTED.name());
        refund.setRequestedByAccountId(requestedByAccountId);
        refund.setIdempotencyKey(idempotencyKey);
        refund.setCreatedByAccountId(requestedByAccountId);
        refund.setUpdatedByAccountId(requestedByAccountId);
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException duplicate) {
            // 并发下同幂等键的第二笔申请：回放既有记录
            RefundRecordEntity existing =
                    refundMapper.selectByIdempotencyKey(payment.getId(), idempotencyKey);
            if (existing != null) {
                return existing;
            }
            throw duplicate;
        }
        return refund;
    }

    private RefundRecordEntity requireByRefundNo(String refundNo) {
        RefundRecordEntity refund = refundMapper.selectByRefundNo(refundNo);
        if (refund == null) {
            throw new BusinessException(ErrorCode.REFUND_NOT_FOUND);
        }
        return refund;
    }
}
