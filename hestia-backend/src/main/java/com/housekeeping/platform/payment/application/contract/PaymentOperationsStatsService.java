package com.housekeeping.platform.payment.application.contract;

import com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 支付模块对运营/统计的只读统计契约（docs/modules/13 第 18.3 节）。
 *
 * <p>运营模块聚合退款数据必须走本契约，禁止直接读 {@code refund_record}。</p>
 */
public interface PaymentOperationsStatsService {

    /**
     * 按退款完成日统计退款笔数与金额（仅 SUCCESS）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期 + 区域分组的退款统计行
     */
    List<RefundDailyStatRow> refundDailyStats(LocalDate from, LocalDate to);

    /**
     * 待处理退款单数（REQUESTED / APPROVED / PROCESSING，运营工作台用）。
     *
     * @param areaIds 授权服务区域；空集合 = 不限区域（ADMIN）
     * @return 待处理退款单数
     */
    long pendingRefundCount(Collection<Long> areaIds);
}
