package com.housekeeping.platform.booking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 取消费用计算器（docs/modules/07 第 10.2、10.3 节，纯函数）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>距服务开始时间 ≥ 免费取消小时数：不收费，全额可退；</li>
 *   <li>不足免费取消小时数：违约金 = max(固定金额, 应付金额 × 费率)，
 *       按"向上取整到分"避免少收；</li>
 *   <li>可退金额 = 应付金额 − 违约金，违约金不会超过应付金额。</li>
 * </ul>
 *
 * <p>规则参数从订单的 {@code cancellation_policy_snapshot_json} 还原，
 * 保证"下单时看到的取消规则"与"取消时实际执行的规则"一致。</p>
 */
public final class CancellationFeeCalculator {

    private CancellationFeeCalculator() {
    }

    /**
     * 计算取消费用。
     *
     * @param payableAmount    应付金额（元）
     * @param appointmentStart 服务开始时间
     * @param now              当前时间
     * @param policy           下单时快照的取消规则
     * @return 费用与可退金额
     */
    public static CancellationFeeResult calculate(BigDecimal payableAmount,
                                                  LocalDateTime appointmentStart,
                                                  LocalDateTime now,
                                                  CancellationFeePolicy policy) {
        long minutesUntilStart = Duration.between(now, appointmentStart).toMinutes();
        // 免费窗口为 0 表示"任意时刻取消都收费"（含服务开始后）
        if (policy.freeCancelHours() > 0 && minutesUntilStart >= policy.freeCancelHours() * 60L) {
            return new CancellationFeeResult(BigDecimal.ZERO.setScale(2), payableAmount);
        }

        BigDecimal rateFee = payableAmount.multiply(policy.lateCancelFeeRate())
                .setScale(2, RoundingMode.CEILING);
        BigDecimal feeAmount = rateFee.max(policy.lateCancelFeeAmount());
        if (feeAmount.compareTo(payableAmount) > 0) {
            feeAmount = payableAmount;
        }
        BigDecimal refundable = payableAmount.subtract(feeAmount);
        return new CancellationFeeResult(feeAmount, refundable);
    }

    /**
     * 取消费用计算结果。
     *
     * @param feeAmount       违约金（元）
     * @param refundableAmount 可退金额（元）
     */
    public record CancellationFeeResult(BigDecimal feeAmount, BigDecimal refundableAmount) {
    }
}
