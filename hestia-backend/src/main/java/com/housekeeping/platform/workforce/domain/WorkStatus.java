package com.housekeeping.platform.workforce.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 人员工作状态（docs/modules/05 第 5.1 节）。
 *
 * <pre>
 * ONBOARDING -> ACTIVE -> SUSPENDED -> ACTIVE
 *                        -> LEFT
 * </pre>
 *
 * <p>{@code LEFT} 是终态。这不是"懒得写恢复路径"：离职后重新入职需要新的劳动关系，
 * 直接把人改回 {@code ACTIVE} 会让历史订单、技能审核记录、证书审核记录
 * 都挂在一个"从未中断过"的履历上，责任追溯就断了。</p>
 */
public enum WorkStatus {

    /** 入职或资料审核中。 */
    ONBOARDING,

    /** 可正常参与排班和派单。 */
    ACTIVE,

    /** 临时暂停接单。 */
    SUSPENDED,

    /** 已离职，不可恢复。 */
    LEFT;

    /**
     * 当前状态能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return 允许迁移返回 {@code true}
     */
    public boolean canTransitionTo(WorkStatus target) {
        return target != null && allowedTargets().contains(target);
    }

    /**
     * 当前状态允许迁移到的状态集合。
     *
     * @return 目标状态集合
     */
    public Set<WorkStatus> allowedTargets() {
        return switch (this) {
            case ONBOARDING -> EnumSet.of(ACTIVE);
            case ACTIVE -> EnumSet.of(SUSPENDED, LEFT);
            case SUSPENDED -> EnumSet.of(ACTIVE, LEFT);
            case LEFT -> EnumSet.noneOf(WorkStatus.class);
        };
    }

    /**
     * 是否已离职（终态）。
     *
     * @return 已离职返回 {@code true}
     */
    public boolean isTerminal() {
        return this == LEFT;
    }
}
