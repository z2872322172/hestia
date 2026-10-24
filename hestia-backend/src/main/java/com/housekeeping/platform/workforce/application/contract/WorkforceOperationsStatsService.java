package com.housekeeping.platform.workforce.application.contract;

import java.time.LocalDate;
import java.util.Collection;

/**
 * 人员模块对运营模块的只读统计契约（docs/modules/13 第 18.4 节）。
 *
 * <p>运营模块只允许通过本接口获取人员侧统计，禁止直接查询人员表。</p>
 */
public interface WorkforceOperationsStatsService {

    /**
     * 统计证书即将到期的去重人员数（看板告警"证书即将过期人员"）。
     *
     * @param areaIds   授权服务区域 ID 集合（空 = 不限区域，ADMIN）
     * @param today     基准日
     * @param noticeDays 提前提醒天数（system_config.certificate_expiry_notice_days）
     * @return 去重人员数
     */
    long expiringWorkerCount(Collection<Long> areaIds, LocalDate today, int noticeDays);
}
