package com.housekeeping.platform.customer.domain;

/**
 * 客户档案状态（docs/modules/02 第 5.2 节）。
 *
 * <p>关闭客户档案<b>不等于</b>停用账号：同一个人可能既是消费者又是服务人员，
 * 关闭消费者身份不应影响其接单能力。</p>
 */
public enum CustomerStatus {

    /** 正常使用。 */
    ACTIVE,

    /** 注销或业务关闭。 */
    CLOSED;

    /**
     * 是否允许继续维护地址与下单。
     *
     * @return 允许返回 {@code true}
     */
    public boolean isUsable() {
        return this == ACTIVE;
    }
}
