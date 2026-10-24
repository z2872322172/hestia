package com.housekeeping.platform.fulfillment.domain;

/**
 * 履约状态机（docs/modules/09 第 5 节）。
 *
 * <p>正常链路 PENDING → EN_ROUTE → CHECKED_IN → IN_SERVICE → PENDING_CONFIRM → COMPLETED；
 * 异常链路 EN_ROUTE / CHECKED_IN / IN_SERVICE → EXCEPTION，处理后回 IN_SERVICE 或取消。</p>
 */
public enum FulfillmentStatus {

    PENDING,
    EN_ROUTE,
    CHECKED_IN,
    IN_SERVICE,
    PENDING_CONFIRM,
    COMPLETED,
    EXCEPTION,
    CANCELED;

    /**
     * 状态迁移合法性（docs/modules/09 第 5.1、5.2 节）。
     *
     * @param target 目标状态
     * @return 是否允许迁移
     */
    public boolean canTransitTo(FulfillmentStatus target) {
        return switch (this) {
            case PENDING -> target == EN_ROUTE || target == EXCEPTION || target == CANCELED;
            case EN_ROUTE -> target == CHECKED_IN || target == EXCEPTION || target == CANCELED;
            case CHECKED_IN -> target == IN_SERVICE || target == EXCEPTION || target == CANCELED;
            case IN_SERVICE -> target == PENDING_CONFIRM || target == EXCEPTION || target == CANCELED;
            case PENDING_CONFIRM -> target == COMPLETED || target == CANCELED;
            case EXCEPTION -> target == IN_SERVICE || target == CANCELED;
            case COMPLETED, CANCELED -> false;
        };
    }
}
