package com.housekeeping.platform.fulfillment.application.contract;

import java.util.Collection;

/**
 * 履约模块对运营模块的只读统计契约（docs/modules/13 第 18.1 节）。
 *
 * <p>运营模块只允许通过本接口获取履约侧统计，禁止直接查询履约表。</p>
 */
public interface FulfillmentOperationsStatsService {

    /**
     * 按状态统计履约记录数（看板"今日服务中/异常"、履约监控用）。
     *
     * @param areaIds 授权服务区域 ID 集合（空 = 不限区域，ADMIN）
     * @param status  履约状态（如 IN_SERVICE / PENDING_CONFIRM / EXCEPTION）
     * @return 记录数
     */
    long countByStatus(Collection<Long> areaIds, String status);
}
