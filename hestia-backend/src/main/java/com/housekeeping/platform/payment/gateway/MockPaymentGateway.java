package com.housekeeping.platform.payment.gateway;

import com.housekeeping.platform.common.id.IdGenerator;
import com.housekeeping.platform.payment.domain.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 模拟支付网关（docs/modules/10 第 2.1 条：首版模拟支付，不接真实资金清算）。
 *
 * <p>预下单直接成功并生成渠道交易号；退款直接成功；
 * 签名为 {@code SHA-256(paymentNo|amount|共享密钥)}——密钥只配在服务端与
 * 本地冒烟脚本共享，模拟"回调必须验签"的约束。真实渠道接入时整体替换本类。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final String sharedSecret;

    public MockPaymentGateway(
            @Value("${app.payment.mock-gateway-secret:hestia-mock-gateway-secret}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public GatewayPrepayResult prepay(String paymentNo, BigDecimal amount) {
        return GatewayPrepayResult.ok("MOCK" + IdGenerator.nextId());
    }

    @Override
    public GatewayRefundResult refund(String paymentNo, String refundNo, BigDecimal amount) {
        return GatewayRefundResult.ok("MOCKRF" + IdGenerator.nextId());
    }

    @Override
    public String sign(String paymentNo, BigDecimal amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = paymentNo + "|" + amount.toPlainString() + "|" + sharedSecret;
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", exception);
        }
    }
}
