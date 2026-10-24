package com.housekeeping.platform.payment.application;

import com.housekeeping.platform.payment.application.contract.PaymentOperationsStatsService;
import com.housekeeping.platform.payment.persistence.mapper.RefundRecordMapper;
import com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 支付统计契约实现（docs/modules/13 第 18.3 节）。
 *
 * <p>只读聚合，供运营日报任务与看板使用。</p>
 */
@Service
@RequiresPersistence
public class PaymentStatsService implements PaymentOperationsStatsService {

    private final RefundRecordMapper refundRecordMapper;

    public PaymentStatsService(RefundRecordMapper refundRecordMapper) {
        this.refundRecordMapper = refundRecordMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDailyStatRow> refundDailyStats(LocalDate from, LocalDate to) {
        return refundRecordMapper.selectRefundDailyStats(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public long pendingRefundCount(Collection<Long> areaIds) {
        return refundRecordMapper.countPending(areaIds);
    }
}
