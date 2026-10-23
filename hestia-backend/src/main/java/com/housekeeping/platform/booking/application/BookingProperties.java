package com.housekeeping.platform.booking.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 订单与支付相关配置（docs/modules/07 第 10.3、13 节）。
 *
 * <p>取消费用三项在下单时固化进 {@code cancellation_policy_snapshot_json}，
 * 后续修改只影响新订单，不追溯历史订单。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.booking")
public class BookingProperties {

    /** 待支付订单的支付截止分钟数，超时由定时任务自动取消。 */
    private int paymentTimeoutMinutes = 15;

    /** 免费取消小时数：距服务开始不足该时长时收取违约金。 */
    private int freeCancelHours = 24;

    /** 临近取消费率（占应付金额比例）。 */
    private BigDecimal lateCancelFeeRate = new BigDecimal("0.3");

    /** 临近取消固定违约金（元），与费率取大者。 */
    private BigDecimal lateCancelFeeAmount = new BigDecimal("50");

    /** 支付超时扫描间隔（毫秒）。 */
    private long timeoutSweepIntervalMs = 30_000L;

    /** 单批最多处理的超时订单数。 */
    private int timeoutSweepBatchSize = 50;
}
