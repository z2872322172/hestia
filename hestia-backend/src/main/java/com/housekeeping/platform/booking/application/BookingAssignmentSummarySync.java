package com.housekeeping.platform.booking.application;

import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.application.BookingAssignmentSummarySyncer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 派单摘要同步契约的订单侧实现（依赖倒置：dispatch 只依赖接口）。
 *
 * <p>与派单模块的状态变更处于同一事务（调用方为 {@code REQUIRED} 传播），
 * 摘要列与 {@code order_assignment} / {@code dispatch_job} 原子一致。</p>
 */
@Service
@RequiresPersistence
public class BookingAssignmentSummarySync implements BookingAssignmentSummarySyncer {

    private final BookingOrderMapper orderMapper;

    public BookingAssignmentSummarySync(BookingOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int markAccepted(long bookingId, long workerId) {
        return orderMapper.markAssignmentAccepted(bookingId, workerId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markPendingAccept(long bookingId, long workerId, long workerSlotId) {
        orderMapper.markAssignmentPendingAccept(bookingId, workerId, workerSlotId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markReassigning(long bookingId) {
        orderMapper.markAssignmentReassigning(bookingId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markManualRequired(long bookingId) {
        orderMapper.markAssignmentManualRequired(bookingId);
    }
}
