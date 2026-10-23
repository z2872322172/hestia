package com.housekeeping.platform.location.domain;

/**
 * 服务区域状态（docs/modules/03 第 6.2 节）。
 *
 * <p>只有 {@link #ACTIVE} 参与地址匹配。归档是不可逆的终点：
 * 归档区域保留历史统计与历史订单关系，但不能再恢复为普通经营区域。</p>
 */
public enum ServiceAreaStatus {

    /** 配置中，不参与匹配。 */
    DRAFT,

    /** 可参与地址匹配与新订单。 */
    ACTIVE,

    /** 暂停新增业务，保留历史关系。 */
    INACTIVE,

    /** 归档，不再恢复。 */
    ARCHIVED;

    /**
     * 状态是否允许流转到目标状态。
     *
     * <p>状态机：DRAFT → ACTIVE ↔ INACTIVE；三者都可 → ARCHIVED；ARCHIVED 为终态。
     * 用代码显式表达，而不是在 Service 里散落 {@code if (status != ...)}。</p>
     *
     * @param target 目标状态
     * @return 允许流转返回 {@code true}
     */
    public boolean canTransitionTo(ServiceAreaStatus target) {
        if (this == ARCHIVED) {
            return false;
        }
        if (target == ARCHIVED) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == ACTIVE;
            case ACTIVE -> target == INACTIVE;
            case INACTIVE -> target == ACTIVE;
            default -> false;
        };
    }
}
