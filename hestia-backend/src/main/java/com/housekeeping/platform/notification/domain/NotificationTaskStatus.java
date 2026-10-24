package com.housekeeping.platform.notification.domain;

/**
 * 通知任务状态机（docs/modules/12 第 6 节）。
 *
 * <p>合法流转：</p>
 * <pre>
 * PENDING -> PROCESSING -> SENT
 * PROCESSING -> FAILED -> PENDING（重试）
 * PENDING -> CANCELED
 * </pre>
 *
 * <p>不变量：状态推进全部通过条件更新（WHERE status = 期望值）完成，
 * 影响行数为 0 即并发冲突，调用方必须放弃本次处理而不是重读强改；
 * FAILED 不是终态——重试次数未耗尽时回到 PENDING，耗尽后停留 FAILED 等人工处理。</p>
 */
public enum NotificationTaskStatus {

    /** 待发送。 */
    PENDING,

    /** 发送中（已被调度器认领）。 */
    PROCESSING,

    /** 发送成功（终态）。 */
    SENT,

    /** 发送失败：重试未耗尽会回到 PENDING，耗尽后等待人工处理。 */
    FAILED,

    /** 已取消（收件人禁用或业务方撤回，终态）。 */
    CANCELED;

    /**
     * 判断当前状态是否允许流转到目标状态。
     *
     * @param target 目标状态
     * @return true 表示允许
     */
    public boolean canTransitionTo(NotificationTaskStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING || target == CANCELED;
            case PROCESSING -> target == SENT || target == FAILED;
            // FAILED 回 PENDING 是"重新入队"的重试路径
            case FAILED -> target == PENDING;
            case SENT, CANCELED -> false;
        };
    }
}
