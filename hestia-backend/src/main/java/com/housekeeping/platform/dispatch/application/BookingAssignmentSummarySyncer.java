package com.housekeeping.platform.dispatch.application;

/**
 * 订单派单摘要同步契约。
 *
 * <p>订单详情页的 {@code assignment_status} 与 {@code current_worker_id} 读自
 * {@code booking_order} 摘要列（批次 3 第一轮决策）。接单/改派由派单模块驱动，
 * 摘要列归订单模块所有——派单模块只依赖本契约，订单模块提供实现，
 * 避免 dispatch → booking 的编译依赖（依赖倒置）。</p>
 */
public interface BookingAssignmentSummarySyncer {

    /**
     * 人员接单：摘要 PENDING_ACCEPT → ACCEPTED，写入当前人员与接单时间。
     *
     * <p>返回 0 行说明订单状态已不允许（如已取消），调用方应视为冲突回滚。</p>
     *
     * @param bookingId 订单 ID
     * @param workerId  接单人员 ID
     * @return 影响行数，1 成功 / 0 摘要状态已变
     */
    int markAccepted(long bookingId, long workerId);

    /**
     * 改派落到新人员：摘要 → PENDING_ACCEPT，更新当前人员与时段。
     *
     * @param bookingId    订单 ID
     * @param workerId     新人员 ID
     * @param workerSlotId 新人员时段 ID
     */
    void markPendingAccept(long bookingId, long workerId, long workerSlotId);

    /**
     * 进入改派：摘要 PENDING_ACCEPT → REASSIGNING。
     *
     * @param bookingId 订单 ID
     */
    void markReassigning(long bookingId);

    /**
     * 无候选可派：摘要 REASSIGNING → MANUAL_REQUIRED。
     *
     * @param bookingId 订单 ID
     */
    void markManualRequired(long bookingId);
}
