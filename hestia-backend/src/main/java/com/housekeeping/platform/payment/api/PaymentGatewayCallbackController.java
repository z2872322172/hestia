package com.housekeeping.platform.payment.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.payment.application.PaymentApplicationService;
import com.housekeeping.platform.payment.application.view.PaymentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付渠道回调入口（docs/modules/10 第 7.2 节）。
 *
 * <p>回调方是外部网关，不带平台用户令牌，鉴权依赖签名校验（应用层验签 +
 * 幂等占位），安全配置对 {@code /api/v1/payment/callback/**} 放行。
 * 首版只有 Mock 渠道；接入真实渠道时按 {@code channel} 分发到对应处理器。</p>
 */
@RestController
@RequestMapping("/api/v1/payment/callback")
@Validated
@RequiresPersistence
@Tag(name = "支付回调", description = "支付渠道异步回调（网关侧调用）")
public class PaymentGatewayCallbackController {

    private final PaymentApplicationService paymentService;

    public PaymentGatewayCallbackController(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Mock 渠道支付成功回调。
     *
     * <p>重复回调返回 200 + DUPLICATE 状态，让网关停止重试；
     * 验签失败 / 金额不符抛 {@code PAYMENT_CALLBACK_INVALID}。</p>
     *
     * @param request 回调报文
     * @return 处理结果（SUCCESS / DUPLICATE_CALLBACK）
     */
    @PostMapping("/mock")
    @Operation(summary = "Mock 渠道支付回调")
    public Result<PaymentView> mockCallback(@Valid @RequestBody MockCallbackRequest request) {
        return Result.ok(paymentService.handleMockCallback(
                new PaymentApplicationService.MockCallbackCommand(request.paymentNo(),
                        request.callbackId(), request.tradeNo(), request.amount(),
                        request.sign(), request.paidAt())));
    }

    /**
     * Mock 渠道回调报文。
     *
     * @param paymentNo  支付单号
     * @param callbackId 回调唯一 ID（幂等键）
     * @param tradeNo    渠道交易号
     * @param amount     支付金额
     * @param sign       签名 SHA-256(paymentNo|amount|共享密钥)
     * @param paidAt     支付时间，可空（默认服务端时间）
     */
    public record MockCallbackRequest(@NotBlank String paymentNo,
                                      @NotBlank String callbackId,
                                      String tradeNo,
                                      @NotNull BigDecimal amount,
                                      @NotBlank String sign,
                                      LocalDateTime paidAt) {
    }
}
