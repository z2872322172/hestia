package com.housekeeping.platform.payment.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.payment.application.PaymentApplicationService;
import com.housekeeping.platform.payment.application.RefundApplicationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营支付与退款接口（docs/modules/10 第 15.2 节）。
 *
 * <p>运营视角：支付/退款流水监控 + 退款审批（approve 同步执行、reject 带原因、
 * retry 重试失败退款）。审批执行的结果语义见 {@link RefundApplicationService}。</p>
 */
@RestController
@RequestMapping("/api/v1/operator")
@Validated
@RequiresPersistence
@Tag(name = "运营-支付退款", description = "支付退款流水与退款审批")
public class OperatorPaymentController {

    private final PaymentApplicationService paymentService;

    private final RefundApplicationService refundService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorPaymentController(PaymentApplicationService paymentService,
                                     RefundApplicationService refundService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 支付流水列表。
     *
     * @param status 状态过滤，可空
     * @param page   页码
     * @param size   每页大小
     * @return 分页支付列表
     */
    @GetMapping("/payments")
    @Operation(summary = "支付流水列表")
    public Result<PageResult<PaymentView>> payments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<PaymentView> records = paymentService.operatorList(page, size, status);
        long total = paymentService.operatorCount(status);
        return Result.ok(PageResult.of(records, page, size, total));
    }

    /**
     * 退款流水列表。
     *
     * @param status 状态过滤，可空
     * @param page   页码
     * @param size   每页大小
     * @return 分页退款列表
     */
    @GetMapping("/refunds")
    @Operation(summary = "退款流水列表")
    public Result<PageResult<RefundView>> refunds(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<RefundView> records = refundService.operatorList(page, size, status);
        long total = refundService.operatorCount(status);
        return Result.ok(PageResult.of(records, page, size, total));
    }

    /**
     * 审批通过并执行退款。
     *
     * @param refundNo 退款单号
     * @return 退款详情
     */
    @PostMapping("/refunds/{refundNo}/approve")
    @Operation(summary = "退款审批通过", description = "通过后同一事务内执行网关退款")
    public Result<RefundView> approve(@PathVariable String refundNo) {
        return Result.ok(refundService.approveAndExecute(refundNo,
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 拒绝退款申请。
     *
     * @param refundNo 退款单号
     * @param request  拒绝原因
     * @return 退款详情
     */
    @PostMapping("/refunds/{refundNo}/reject")
    @Operation(summary = "退款审批拒绝")
    public Result<RefundView> reject(@PathVariable String refundNo,
                                     @Valid @RequestBody RejectRequest request) {
        return Result.ok(refundService.reject(refundNo,
                currentAccountResolver.requireAccountId(), request.reasonText()));
    }

    /**
     * 重试失败的退款。
     *
     * @param refundNo 退款单号
     * @return 退款详情
     */
    @PostMapping("/refunds/{refundNo}/retry")
    @Operation(summary = "重试失败退款")
    public Result<RefundView> retry(@PathVariable String refundNo) {
        return Result.ok(refundService.retry(refundNo));
    }

    /**
     * 拒绝退款请求体。
     *
     * @param reasonText 拒绝原因
     */
    public record RejectRequest(@NotBlank String reasonText) {
    }
}
