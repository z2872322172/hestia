package com.housekeeping.platform.booking.domain;

import java.math.BigDecimal;

/**
 * 取消规则快照（与 {@code booking_order.cancellation_policy_snapshot_json} 对应）。
 *
 * <p>下单时从配置（{@code app.booking.free-cancel-hours} 等）读取并固化到订单，
 * 取消时按快照执行，规则变更不影响历史订单。</p>
 *
 * @param freeCancelHours     免费取消小时数
 * @param lateCancelFeeRate   临近取消费率（占应付金额比例，如 0.3）
 * @param lateCancelFeeAmount 临近取消固定违约金（元）
 */
public record CancellationFeePolicy(int freeCancelHours,
                                    BigDecimal lateCancelFeeRate,
                                    BigDecimal lateCancelFeeAmount) {
}
