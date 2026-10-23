package com.housekeeping.platform.customer.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.persistence.mapper.CustomerAddressMapper;
import com.housekeeping.platform.location.application.ServiceAreaChangeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 服务区域变更后把受影响地址标记为 {@code STALE}。
 *
 * <p>为什么由客户模块实现而不是位置模块直接更新 {@code customer_address}：
 * 那张表的状态约束（{@code area_match_status} 取值、{@code chk_customer_address_matched_area}）
 * 属于客户模块，跨模块直接写库会让约束散落在两个模块里，
 * 将来改一处忘了另一处就会出现非法状态。</p>
 */
@Component
@RequiresPersistence
public class CustomerAddressStaleHandler implements ServiceAreaChangeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerAddressStaleHandler.class);

    private final CustomerAddressMapper addressMapper;

    public CustomerAddressStaleHandler(CustomerAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public void onServiceAreaChanged(long serviceAreaId, String reason) {
        int affected = addressMapper.markStaleByServiceAreaId(serviceAreaId);
        if (affected > 0) {
            // 只记录服务区域 ID 与条数，不记录任何地址内容
            LOG.info("服务区域变更，地址标记为 STALE serviceAreaId={} affected={} reason={}",
                    serviceAreaId, affected, reason);
        }
    }
}
