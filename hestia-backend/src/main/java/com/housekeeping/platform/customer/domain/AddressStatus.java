package com.housekeeping.platform.customer.domain;

/**
 * 地址状态（docs/modules/02 第 6.3 节）。
 *
 * <p>软删除：保留主键与历史关系，但订单必须使用自己的地址快照，
 * 不允许"按 addressId 回查地址簿"来还原历史订单地址。</p>
 */
public enum AddressStatus {

    ACTIVE,

    DELETED
}
