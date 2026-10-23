package com.housekeeping.platform.catalog.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 服务项目状态（docs/modules/04 第 6.1 节）。
 *
 * <p>状态机只有三条合法边：{@code DRAFT → PUBLISHED → OFFLINE → PUBLISHED}。
 * 没有"草稿直接下架"这条路——草稿本来就没上过架，下架只会让 {@code offline_at} 变成噪音。</p>
 *
 * <p>已发布的服务不物理删除：订单快照要能回溯到"当时卖的是什么"。</p>
 */
public enum ServiceItemStatus {

    /** 草稿，不可销售。 */
    DRAFT,

    /** 已发布，可查询、可下单。 */
    PUBLISHED,

    /** 已下架，不可创建新订单，历史订单不受影响。 */
    OFFLINE;

    /**
     * 判断能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return 是否允许
     */
    public boolean canTransitionTo(ServiceItemStatus target) {
        return switch (this) {
            case DRAFT -> target == PUBLISHED;
            case PUBLISHED -> target == OFFLINE;
            case OFFLINE -> target == PUBLISHED;
        };
    }

    /**
     * 当前状态下所有可迁移的目标。
     *
     * @return 目标集合，可能为空
     */
    public Set<ServiceItemStatus> allowedTargets() {
        Set<ServiceItemStatus> targets = EnumSet.noneOf(ServiceItemStatus.class);
        for (ServiceItemStatus candidate : values()) {
            if (canTransitionTo(candidate)) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    /**
     * 是否可被消费者看到并下单。
     *
     * @return 仅 {@code PUBLISHED} 为 {@code true}
     */
    public boolean isSellable() {
        return this == PUBLISHED;
    }
}
