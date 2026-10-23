package com.housekeeping.platform.payment.domain;

import java.math.BigDecimal;

/**
 * 支付网关防腐接口（docs/modules/10 第 2.11 条）。
 *
 * <p>真实渠道（微信 / 支付宝）接入时新增实现类，支付应用服务不感知渠道差异。
 * 首版只提供 Mock 实现：prepay 直接可支付，refund 直接成功，
 * {@link #sign} 提供对称签名供回调验签（模拟渠道的"防伪造回调"）。</p>
 */
public interface PaymentGateway {

    /**
     * 渠道编码（MOCK / WECHAT / ALIPAY，与 payment_record.channel CHECK 一致）。
     *
     * @return 渠道编码
     */
    String channel();

    /**
     * 发起支付（预下单）。
     *
     * @param paymentNo 支付单号
     * @param amount    支付金额
     * @return 预下单结果（含渠道交易号或失败原因）
     */
    GatewayPrepayResult prepay(String paymentNo, BigDecimal amount);

    /**
     * 发起退款。
     *
     * @param paymentNo 原支付单号
     * @param refundNo  退款单号
     * @param amount    退款金额
     * @return 退款执行结果（外部失败必须如实返回，不能伪装成功）
     */
    GatewayRefundResult refund(String paymentNo, String refundNo, BigDecimal amount);

    /**
     * 计算回调签名（Mock 渠道：SHA-256(paymentNo|amount|共享密钥)）。
     *
     * @param paymentNo 支付单号
     * @param amount    支付金额
     * @return 十六进制签名
     */
    String sign(String paymentNo, BigDecimal amount);

    /**
     * 预下单结果。
     *
     * @param success    是否成功
     * @param tradeNo    渠道交易号（失败为 null）
     * @param payParams  客户端拉起支付所需参数（Mock 为空）
     * @param errorMessage 失败原因
     */
    record GatewayPrepayResult(boolean success, String tradeNo, java.util.Map<String, String> payParams,
                               String errorMessage) {

        /** 构造成功结果。 */
        public static GatewayPrepayResult ok(String tradeNo) {
            return new GatewayPrepayResult(true, tradeNo, java.util.Map.of(), null);
        }

        /** 构造失败结果。 */
        public static GatewayPrepayResult fail(String errorMessage) {
            return new GatewayPrepayResult(false, null, java.util.Map.of(), errorMessage);
        }
    }

    /**
     * 退款执行结果。
     *
     * @param success      是否成功
     * @param channelRefundNo 渠道退款流水号（失败为 null）
     * @param errorMessage 失败原因
     */
    record GatewayRefundResult(boolean success, String channelRefundNo, String errorMessage) {

        /** 构造成功结果。 */
        public static GatewayRefundResult ok(String channelRefundNo) {
            return new GatewayRefundResult(true, channelRefundNo, null);
        }

        /** 构造失败结果。 */
        public static GatewayRefundResult fail(String errorMessage) {
            return new GatewayRefundResult(false, null, errorMessage);
        }
    }
}
