package com.housekeeping.platform.fulfillment.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.fulfillment.application.contract.FulfillmentOperationsStatsService;
import com.housekeeping.platform.fulfillment.persistence.mapper.ServiceRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * 履约统计契约实现（docs/modules/13 第 18.1 节）。
 *
 * <p>只读聚合，区域裁剪经订单表联表完成，供运营看板与履约监控使用。</p>
 */
@Service
@RequiresPersistence
public class FulfillmentStatsService implements FulfillmentOperationsStatsService {

    private final ServiceRecordMapper recordMapper;

    public FulfillmentStatsService(ServiceRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(Collection<Long> areaIds, String status) {
        return recordMapper.countByStatusInAreas(areaIds, status);
    }
}
