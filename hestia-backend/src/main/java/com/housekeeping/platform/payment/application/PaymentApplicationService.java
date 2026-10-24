package com.housekeeping.platform.payment.application;

import com.housekeeping.platform.booking.application.contract.PayableOrderRef;
import com.housekeeping.platform.booking.application.contract.PaymentOrderQueryService;
import com.housekeeping.platform.booking.application.BookingStatusCommandService;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.id.IdGenerator;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.contract.CustomerQueryService;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.payment.application.view.PaymentCreatedView;
import com.housekeeping.platform.payment.application.view.PaymentView;
import com.housekeeping.platform.payment.domain.PaymentGateway;
import com.housekeeping.platform.payment.domain.PaymentStatus;
import com.housekeeping.platform.payment.persistence.entity.PaymentCallbackLogEntity;
import com.housekeeping.platform.payment.persistence.entity.PaymentRecordEntity;
import com.housekeeping.platform.payment.persistence.mapper.PaymentCallbackLogMapper;
import com.housekeeping.platform.payment.persistence.mapper.PaymentRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 支付应用服务（docs/modules/10 第 7 节）。
 *
 * <p>三条铁律：</p>
 * <ol>
 *   <li><b>金额以服务端为准</b>：创建支付时金额永远取订单应付金额，
 *       客户端传入的金额只用于展示；回调侧再次校验金额一致；</li>
 *   <li><b>回调幂等</b>：先插 RECEIVED 占位行抢占 (channel, callback_id)，
 *       插入冲突即重复回调直接返回成功；状态条件更新兜底"已 SUCCESS"的重放；</li>
 *   <li><b>成功支付唯一</b>：生成列唯一索引在库内保证一单最多一条 SUCCESS，
 *       重复支付尝试最多走到 PAYING，不会双写订单。</li>
 * </ol>
 */
@Service
@RequiresPersistence
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private static final String SEQ_PAYMENT = "PAYMENT";

    private static final String PREFIX_PAYMENT = "PAY";

    private static final String CHANNEL_MOCK = "MOCK";

    private final PaymentRecordMapper paymentMapper;

    private final PaymentCallbackLogMapper callbackLogMapper;

    private final PaymentOrderQueryService paymentOrderQueryService;

    private final CustomerQueryService customerQueryService;

    private final BookingStatusCommandService bookingStatusCommandService;

    private final PaymentGateway gateway;

    private final BusinessNoGenerator noGenerator;

    public PaymentApplicationService(PaymentRecordMapper paymentMapper,
                                     PaymentCallbackLogMapper callbackLogMapper,
                                     PaymentOrderQueryService paymentOrderQueryService,
                                     CustomerQueryService customerQueryService,
                                     BookingStatusCommandService bookingStatusCommandService,
                                     PaymentGateway gateway,
                                     BusinessNoGenerator noGenerator) {
        this.paymentMapper = paymentMapper;
        this.callbackLogMapper = callbackLogMapper;
        this.paymentOrderQueryService = paymentOrderQueryService;
        this.customerQueryService = customerQueryService;
        this.bookingStatusCommandService = bookingStatusCommandService;
        this.gateway = gateway;
        this.noGenerator = noGenerator;
    }

    /**
     * 创建支付单并预下单（docs/modules/10 第 7.1 节）。
     *
     * <p>幂等键命中既有支付单时直接回放（同订单），幂等键复用到其他订单视为冲突。</p>
     *
     * @param accountId      客户账号 ID
     * @param orderNo        订单号
     * @param channel        支付渠道（首版仅 MOCK）
     * @param idempotencyKey 幂等键，可空
     * @return 支付创建结果
     */
    @Transactional
    public PaymentCreatedView createPayment(long accountId, String orderNo, String channel,
                                            String idempotencyKey) {
        if (!gateway.channel().equals(channel)) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_DISABLED);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            PaymentRecordEntity existing =
                    paymentMapper.selectByIdempotencyKey(accountId, idempotencyKey);
            if (existing != null) {
                if (existing.getBookingId() != requireOwnedBookingId(accountId, orderNo)) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                return new PaymentCreatedView(existing.getPaymentNo(), existing.getAmount(),
                        existing.getChannel(), existing.getStatus());
            }
        }
        long customerId = customerQueryService.requireUsable(accountId).customerId();
        PayableOrderRef ref = paymentOrderQueryService.requirePayable(customerId, orderNo);

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setPaymentNo(noGenerator.next(SEQ_PAYMENT, PREFIX_PAYMENT));
        payment.setBookingId(ref.bookingId());
        payment.setAccountId(accountId);
        payment.setAttemptNo(paymentMapper.selectMaxAttemptNo(ref.bookingId()) + 1);
        payment.setChannel(channel);
        payment.setAmount(ref.payableAmount());
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.CREATED.name());
        payment.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank()
                ? "AUTO-" + IdGenerator.nextId() : idempotencyKey);
        payment.setCreatedByAccountId(accountId);
        payment.setUpdatedByAccountId(accountId);
        paymentMapper.insert(payment);

        // 预下单：CREATED → PAYING；渠道失败如实报错（订单未动，可重新发起）
        PaymentGateway.GatewayPrepayResult prepay =
                gateway.prepay(payment.getPaymentNo(), payment.getAmount());
        if (!prepay.success()) {
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, prepay.errorMessage());
        }
        if (paymentMapper.markPaying(payment.getPaymentNo(), prepay.tradeNo()) == 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        return new PaymentCreatedView(payment.getPaymentNo(), payment.getAmount(),
                channel, PaymentStatus.PAYING.name());
    }

    /**
     * 处理模拟渠道回调（docs/modules/10 第 7.2 节）。
     *
     * <p>流程：占位日志 → 验签 → 查支付单 → 金额校验 → 条件置 SUCCESS →
     * 日志终态 → 订单 markPaid。任何一步失败日志都会留下 FAILED 轨迹。</p>
     *
     * @param request 回调报文
     * @return 回调处理结果
     */
    @Transactional
    public PaymentView handleMockCallback(MockCallbackCommand request) {
        // 1) 幂等占位：(channel, callback_id) 唯一，重复回调在这里被挡住
        PaymentCallbackLogEntity logEntity = new PaymentCallbackLogEntity();
        logEntity.setChannel(CHANNEL_MOCK);
        logEntity.setCallbackId(request.callbackId());
        logEntity.setPaymentNo(request.paymentNo());
        logEntity.setPayloadHash(payloadHash(request));
        logEntity.setResult("RECEIVED");
        try {
            callbackLogMapper.insert(logEntity);
        } catch (DuplicateKeyException duplicate) {
            return PaymentView.duplicateCallback(request.paymentNo());
        }

        // 2) 支付单存在性
        PaymentRecordEntity payment = paymentMapper.selectByPaymentNo(request.paymentNo());
        if (payment == null) {
            return failCallback(logEntity.getId(), "支付单不存在");
        }
        // 3) 验签 + 金额校验：金额以支付单为准，回调金额不符一律拒绝
        if (!gateway.sign(payment.getPaymentNo(), payment.getAmount()).equals(request.sign())) {
            return failCallback(logEntity.getId(), "回调验签失败");
        }
        if (payment.getAmount().compareTo(request.amount()) != 0) {
            return failCallback(logEntity.getId(), "回调金额与支付单不一致");
        }
        // 4) 条件置 SUCCESS：0 行说明状态已被推进（重放 / 已关闭），回查定性
        LocalDateTime paidAt = request.paidAt() != null ? request.paidAt() : LocalDateTime.now();
        if (paymentMapper.markSuccess(payment.getPaymentNo(), paidAt) == 0) {
            PaymentRecordEntity latest = paymentMapper.selectByPaymentNo(payment.getPaymentNo());
            if (latest != null && PaymentStatus.SUCCESS.name().equals(latest.getStatus())) {
                callbackLogMapper.finish(logEntity.getId(), "DUPLICATE", "重复回调");
                return PaymentView.duplicateCallback(request.paymentNo());
            }
            return failCallback(logEntity.getId(),
                    "支付单状态为 " + (latest != null ? latest.getStatus() : "未知") + "，不能置为成功");
        }
        callbackLogMapper.finish(logEntity.getId(), "SUCCESS", null);
        // 5) 订单确认（同一事务：时段 BOOKED、派单激活、状态日志一起提交）
        bookingStatusCommandService.markPaidByBookingId(payment.getBookingId(),
                payment.getAmount(), paidAt);
        return PaymentView.from(paymentMapper.selectByPaymentNo(payment.getPaymentNo()));
    }

    /**
     * 关闭订单全部未完成支付单（支付超时 / 订单取消联动，docs/modules/10 第 7.4 节）。
     *
     * @param bookingId 订单 ID
     */
    @Transactional
    public void closeForBooking(long bookingId) {
        paymentMapper.closeForBooking(bookingId);
    }

    /**
     * 客户查询自己的支付单。
     *
     * @param accountId 客户账号 ID
     * @param paymentNo 支付单号
     * @return 支付详情
     */
    @Transactional(readOnly = true)
    public PaymentView customerPayment(long accountId, String paymentNo) {
        PaymentRecordEntity payment = requireByPaymentNo(paymentNo);
        if (payment.getAccountId() == null || payment.getAccountId() != accountId) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_OWNED);
        }
        return PaymentView.from(payment);
    }

    /**
     * 客户查询订单的支付流水。
     *
     * @param accountId 客户账号 ID
     * @param orderNo   订单号
     * @return 支付流水列表
     */
    @Transactional(readOnly = true)
    public List<PaymentView> customerPaymentsOfBooking(long accountId, String orderNo) {
        long bookingId = requireOwnedBookingId(accountId, orderNo);
        return paymentMapper.selectByBookingId(bookingId).stream().map(PaymentView::from).toList();
    }

    /**
     * 运营支付列表。
     *
     * @param page   页码
     * @param size   每页大小
     * @param status 状态过滤，可空
     * @return 分页支付列表
     */
    @Transactional(readOnly = true)
    public List<PaymentView> operatorList(int page, int size, String status) {
        return paymentMapper.selectPage(status, (long) (page - 1) * size, size)
                .stream().map(PaymentView::from).toList();
    }

    /**
     * 运营支付总数。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Transactional(readOnly = true)
    public long operatorCount(String status) {
        return paymentMapper.count(status);
    }

    private long requireOwnedBookingId(long accountId, String orderNo) {
        long customerId = customerQueryService.requireUsable(accountId).customerId();
        return paymentOrderQueryService.requireOwnedBookingId(customerId, orderNo);
    }

    private PaymentRecordEntity requireByPaymentNo(String paymentNo) {
        PaymentRecordEntity payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    private PaymentView failCallback(Long logId, String errorMessage) {
        callbackLogMapper.finish(logId, "FAILED", errorMessage);
        throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_INVALID, errorMessage);
    }

    private String payloadHash(MockCallbackCommand request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = request.paymentNo() + "|" + request.callbackId() + "|"
                    + request.amount().toPlainString();
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", exception);
        }
    }

    /**
     * 模拟渠道回调命令（Controller 层 DTO 转换而来）。
     *
     * @param paymentNo  支付单号
     * @param callbackId 渠道回调唯一 ID
     * @param tradeNo    渠道交易号
     * @param amount     回调金额
     * @param sign       签名
     * @param paidAt     支付时间，可空
     */
    public record MockCallbackCommand(String paymentNo, String callbackId, String tradeNo,
                                      java.math.BigDecimal amount, String sign,
                                      LocalDateTime paidAt) {
    }
}
