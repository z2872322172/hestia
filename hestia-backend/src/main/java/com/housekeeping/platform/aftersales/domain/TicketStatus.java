package com.housekeeping.platform.aftersales.domain;

import java.util.Set;

/**
 * 售后工单状态机（docs/modules/11 第 6.3 节）。
 *
 * <pre>
 * SUBMITTED → PROCESSING / REJECTED
 * PROCESSING → PENDING_* / RESOLVED / REJECTED
 * PENDING_* → PROCESSING / RESOLVED / REJECTED
 * RESOLVED / REJECTED → CLOSED
 * </pre>
 */
public enum TicketStatus {

    /** 已提交，待运营受理。 */
    SUBMITTED,

    /** 处理中。 */
    PROCESSING,

    /** 待客户确认处理方案。 */
    PENDING_CUSTOMER,

    /** 待人员说明。 */
    PENDING_WORKER,

    /** 待运营复核。 */
    PENDING_REVIEW,

    /** 已解决（结论已形成）。 */
    RESOLVED,

    /** 已驳回（投诉不成立）。 */
    REJECTED,

    /** 已关闭（终态）。 */
    CLOSED;

    private static final Set<TicketStatus> PENDING_SET =
            Set.of(PENDING_CUSTOMER, PENDING_WORKER, PENDING_REVIEW);

    private static final Set<TicketStatus> ACTIVE_SET =
            Set.of(PROCESSING, PENDING_CUSTOMER, PENDING_WORKER, PENDING_REVIEW);

    private static final Set<TicketStatus> SETTLED_SET = Set.of(RESOLVED, REJECTED);

    /**
     * 状态迁移合法性（docs/modules/11 第 6.3 节转换示例）：
     * SUBMITTED 未受理不能直接形成结论（RESOLVED），只能受理或驳回。
     *
     * @param target 目标状态
     * @return 是否允许迁移
     */
    public boolean canTransitTo(TicketStatus target) {
        return switch (this) {
            case SUBMITTED -> target == PROCESSING || target == REJECTED;
            case PROCESSING -> ACTIVE_SET.contains(target) || SETTLED_SET.contains(target);
            case PENDING_CUSTOMER, PENDING_WORKER, PENDING_REVIEW ->
                    target == PROCESSING || SETTLED_SET.contains(target);
            case RESOLVED, REJECTED -> target == CLOSED;
            case CLOSED -> false;
        };
    }

    /**
     * 是否为待反馈的挂起状态（PENDING_*）。
     *
     * @return 是否挂起
     */
    public boolean isPendingFeedback() {
        return PENDING_SET.contains(this);
    }
}
