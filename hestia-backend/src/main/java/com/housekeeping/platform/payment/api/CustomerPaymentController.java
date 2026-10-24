package com.housekeeping.platform.payment.api;

import com.housekeeping.platform.booking.application.BookingQueryService;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.customer.application.contract.CustomerQueryService;
import com.housekeeping.platform.payment.application.PaymentApplicationService;
import com.housekeeping.platform.payment.application.RefundApplicationService;
import com.housekeeping.platform.payment.application.view.PaymentCreatedView;
import com.housekeeping.platform.payment.application.view.PaymentView;
import com.housekeeping.platform.payment.application.view.RefundView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 消费者支付与退款接口（docs/modules/10 第 15.1 节）。
 *
 * <p>归属校验全部下沉到应用服务：支付单按付款账号、订单按客户归属，
 * 他人订单/支付单一律返回"不存在"口径。</p>
 */
@RestController
@RequestMapping("/api/v1/customer")
@Validated
@RequiresPersistence
@Tag(name = "消费者-支付退款", description = "发起支付、支付流水与退款申请")
public class CustomerPaymentController {

    private final PaymentApplicationService paymentService;

    private final RefundApplicationService refundService;

    private final BookingQueryService bookingQueryService;

    private final CustomerQueryService customerQueryService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerPaymentController(PaymentApplicationService paymentService,
                                     RefundApplicationService refundService,
                                     BookingQueryService bookingQueryService,
                                     CustomerQueryService customerQueryService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.bookingQueryService = bookingQueryService;
        this.customerQueryService = customerQueryService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 发起支付。
     *
     * @param orderNo        订单号
     * @param idempotencyKey 幂等键，可空
     * @param request        支付请求
     * @return 支付创建结果
     */
    @PostMapping("/bookings/{orderNo}/pay")
    @Operation(summary = "发起支付", description = "金额以服务端应付金额为准，客户端金额仅展示")
    public Result<PaymentCreatedView> pay(
            @PathVariable String orderNo,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayRequest request) {
        return Result.ok(paymentService.createPayment(currentAccountResolver.requireAccountId(),
                orderNo, request.channel(), idempotencyKey));
    }

    /**
     * 支付单详情。
     *
     * @param paymentNo 支付单号
     * @return 支付详情
     */
    @GetMapping("/payments/{paymentNo}")
    @Operation(summary = "支付单详情")
    public Result<PaymentView> payment(@PathVariable String paymentNo) {
        return Result.ok(paymentService.customerPayment(
                currentAccountResolver.requireAccountId(), paymentNo));
    }

    /**
     * 订单支付流水。
     *
     * @param orderNo 订单号
     * @return 支付流水列表
     */
    @GetMapping("/bookings/{orderNo}/payments")
    @Operation(summary = "订单支付流水")
    public Result<List<PaymentView>> payments(@PathVariable String orderNo) {
        return Result.ok(paymentService.customerPaymentsOfBooking(
                currentAccountResolver.requireAccountId(), orderNo));
    }

    /**
     * 发起退款申请（剩余可退全额）。
     *
     * @param orderNo        订单号
     * @param idempotencyKey 幂等键，可空
     * @param request        退款请求
     * @return 退款详情
     */
    @PostMapping("/bookings/{orderNo}/refund-requests")
    @Operation(summary = "发起退款申请", description = "退款需运营审批后执行")
    public Result<RefundView> requestRefund(
            @PathVariable String orderNo,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {
        long accountId = currentAccountResolver.requireAccountId();
        long customerId = customerQueryService.requireUsable(accountId).customerId();
        long bookingId = bookingQueryService.requireOwnedBookingId(customerId, orderNo);
        // 归属校验在 customerPaymentsOfBooking 内；退款只认最新一笔成功支付
        List<PaymentView> payments = paymentService.customerPaymentsOfBooking(accountId, orderNo);
        PaymentView success = payments.stream()
                .filter(payment -> "SUCCESS".equals(payment.status()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_ALLOWED));
        BigDecimal refundable = refundService.refundableAmount(success.paymentId());
        RefundView refund = RefundView.from(refundService.createRequest(success.paymentId(),
                refundable, request.reasonCode(), request.reasonText(), accountId,
                idempotencyKey == null || idempotencyKey.isBlank()
                        ? "CUSTOMER_REFUND:" + bookingId : idempotencyKey));
        return Result.ok(refund);
    }

    /**
     * 退款单详情。
     *
     * @param refundNo 退款单号
     * @return 退款详情
     */
    @GetMapping("/refunds/{refundNo}")
    @Operation(summary = "退款单详情")
    public Result<RefundView> refund(@PathVariable String refundNo) {
        return Result.ok(refundService.customerRefund(
                currentAccountResolver.requireAccountId(), refundNo));
    }

    /**
     * 发起支付请求体。
     *
     * @param channel 支付渠道（首版仅 MOCK）
     */
    public record PayRequest(@NotBlank String channel) {
    }

    /**
     * 退款申请请求体。
     *
     * @param reasonCode 原因编码
     * @param reasonText 原因描述
     */
    public record RefundRequest(@NotBlank String reasonCode, String reasonText) {
    }
}
