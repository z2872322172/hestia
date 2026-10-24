package com.housekeeping.platform.workforce.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.workforce.application.contract.WorkforceOperationsStatsService;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerCertificateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;

/**
 * 人员统计契约实现（docs/modules/13 第 18.4 节）。
 *
 * <p>只读聚合，供运营看板告警使用；证书过期判定沿用
 * {@code valid_status = 'VALID'} 口径，与证书有效期任务保持一致。</p>
 */
@Service
@RequiresPersistence
public class WorkforceStatsService implements WorkforceOperationsStatsService {

    private final WorkerCertificateMapper certificateMapper;

    public WorkforceStatsService(WorkerCertificateMapper certificateMapper) {
        this.certificateMapper = certificateMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public long expiringWorkerCount(Collection<Long> areaIds, LocalDate today, int noticeDays) {
        return certificateMapper.countExpiringWorkers(areaIds, today, today.plusDays(noticeDays));
    }
}
