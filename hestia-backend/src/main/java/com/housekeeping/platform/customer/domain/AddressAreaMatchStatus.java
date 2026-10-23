package com.housekeeping.platform.customer.domain;

/**
 * 地址的服务区域匹配状态（docs/modules/02 第 6.4 节）。
 *
 * <p>只有 {@link #MATCHED} 的地址可以进入下单流程，且下单时必须<b>重新</b>校验，
 * 不能只信任地址簿里缓存的结果——服务区域配置可能在下单前刚被改过。</p>
 */
public enum AddressAreaMatchStatus {

    /** 尚未执行服务区域判断。 */
    UNMATCHED,

    /** 已匹配到可服务区域。 */
    MATCHED,

    /** 不在公司服务范围内。 */
    OUT_OF_RANGE,

    /** 区域配置或地址信息变化，需要重新判断。 */
    STALE;

    /**
     * 是否允许生成下单快照。
     *
     * @return 已匹配返回 {@code true}
     */
    public boolean allowsOrdering() {
        return this == MATCHED;
    }
}
