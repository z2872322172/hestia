package com.housekeeping.platform.catalog.domain;

/**
 * 服务分类状态（docs/modules/04 第 5.2 节）。
 *
 * <p>停用分类不影响历史服务项目与历史订单，但不能再新增服务项目，也不在前台展示。
 * 因此"停用"只改状态，不做级联——级联停用服务项目会让在售商品凭空消失。</p>
 */
public enum ServiceCategoryStatus {

    /** 正常可用。 */
    ACTIVE,

    /** 已停用，不可新增服务项目、不前台展示。 */
    DISABLED;

    /**
     * 是否可用于挂载新的服务项目。
     *
     * @return 仅 {@code ACTIVE} 为 {@code true}
     */
    public boolean acceptsNewItems() {
        return this == ACTIVE;
    }
}
