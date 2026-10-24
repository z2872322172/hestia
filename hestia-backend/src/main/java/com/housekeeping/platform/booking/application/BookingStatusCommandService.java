package com.housekeeping.platform.booking.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.housekeeping.platform.booking.application.view.BookingDetailView;
import com.housekeeping.platform.booking.domain.BookingStatus;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingStatusLogEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingStatusLogMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.application.DispatchApplicationService;
import com.housekeeping.platform.scheduling.application.WorkerSlotReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态命令服务：支付确认（docs/modules/07 第 8 节）。
 *
 * <p>支付成功链路（同一事务）：金额核对 → 订单条件更新（PENDING_PAYMENT → CONFIRMED）
 * → 时段确认（HELD → BOOKED）→ 派单激活（WAITING_PAYMENT → PENDING_ACCEPT）→ 状态日志。
 * 中间任何一步失败整体回滚；重复回调在金额一致的前提下幂等返回。</p>
 */
@Service
@RequiresPersistence
public class BookingStatusCommandService {

    private final BookingOrderMapper orderMapper;

    private final BookingStatusLogMapper statusLogMapper;

    private final WorkerSlotReservationService slotReservation;

    private final DispatchApplicationService dispatchService;

    private final BookingQueryService queryService;

    private final BookingEventPublisher eventPublisher;

    public BookingStatusCommandService(BookingOrderMapper orderMapper,
                                       BookingStatusLogMapper statusLogMapper,
                                       WorkerSlotReservationService slotReservation,
                                       DispatchApplicationService dispatchService,
                                       BookingQueryService queryService,
                                       BookingEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.slotReservation = slotReservation;
        this.dispatchService = dispatchService;
        this.queryService = queryService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 支付成功确认（按订单 ID，支付模块回调内部命令）。
     *
     * <p>payment_record 只存 booking_id，回调侧按 ID 回查订单后复用统一链路。</p>
     *
     * @param bookingId  订单 ID
     * @param paidAmount 实付金额
     * @param paidAt     支付时间
     * @return 确认后的订单详情
     */
    @Transactional
    public BookingDetailView markPaidByBookingId(long bookingId, BigDecimal paidAmount,
                                                 LocalDateTime paidAt) {
        BookingOrderEntity order = orderMapper.selectById(bookingId);
        if (order == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return markPaid(order, paidAmount, paidAt);
    }

    private BookingDetailView markPaid(BookingOrderEntity order, BigDecimal paidAmount,
                                       LocalDateTime paidAt) {
        if (order.getPayableAmount().compareTo(paidAmount) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (BookingStatus.CONFIRMED.name().equals(order.getOrderStatus())) {
            // 已确认即视为回调重放，直接返回当前状态（docs 第 17 节：支付必须幂等）
            return queryService.detail(order);
        }
        if (orderMapper.markPaid(order.getId(), paidAmount, paidAt) == 0) {
            throw new BusinessException(ErrorCode.BOOKING_STATUS_CONFLICT);
        }
        slotReservation.confirm(order.getWorkerSlotId(), order.getId());
        long workerId = dispatchService.activateForPayment(order.getId());
        // 派单激活后把当前服务人员与派单摘要同步回订单：
        // 人员任务页按 current_worker_id 查询，详情页 assignment_status 读自摘要列
        // （updateById 只更新非 null 字段，恰好等价于只补这两个字段）
        if (workerId > 0) {
            BookingOrderEntity patch = new BookingOrderEntity();
            patch.setId(order.getId());
            patch.setCurrentWorkerId(workerId);
            patch.setAssignmentStatus("PENDING_ACCEPT");
            orderMapper.updateById(patch);
        }
        BookingStatusLogEntity logEntity = new BookingStatusLogEntity();
        logEntity.setBookingId(order.getId());
        logEntity.setFromStatus(BookingStatus.PENDING_PAYMENT.name());
        logEntity.setToStatus(BookingStatus.CONFIRMED.name());
        logEntity.setOperationSource("SYSTEM");
        statusLogMapper.insert(logEntity);
        // 事件与支付确认同一事务落 outbox（docs/04 第 10.5 节）；重放路径已提前返回不会重复发
        eventPublisher.publishPaid(order, paidAmount, paidAt, workerId);
        BookingOrderEntity confirmed = orderMapper.selectById(order.getId());
        return queryService.detail(confirmed);
    }

    /**
     * 应用退款到订单摘要（退款执行成功后由支付模块调用，docs/modules/10 第 8.3 节）。
     *
     * <p>累加 {@code refunded_amount} 并推导支付摘要（REFUNDED / PARTIALLY_REFUNDED）；
     * 条件更新保证累计退款不超过实付金额。</p>
     *
     * @param bookingId    订单 ID
     * @param refundAmount 本笔退款金额
     * @throws BusinessException 超出可退金额
     */
    @Transactional
    public void applyRefund(long bookingId, BigDecimal refundAmount) {
        if (orderMapper.applyRefund(bookingId, refundAmount) == 0) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }
    }

    /**
     * 退款被拒时把支付摘要从 REFUNDING 回退为 PAID（仅当尚无成功退款）。
     *
     * @param bookingId 订单 ID
     */
    @Transactional
    public void revertRefunding(long bookingId) {
        orderMapper.update(null, new LambdaUpdateWrapper<BookingOrderEntity>()
                .eq(BookingOrderEntity::getId, bookingId)
                .eq(BookingOrderEntity::getPaymentStatus, "REFUNDING")
                .eq(BookingOrderEntity::getRefundedAmount, BigDecimal.ZERO)
                .set(BookingOrderEntity::getPaymentStatus, "PAID"));
    }

    // ------------------------------------------------------------------
    // 履约驱动的状态推进（docs/modules/09 第 5.3 节）
    // ------------------------------------------------------------------

    /**
     * 履约推进订单主状态（履约模块内部命令，条件更新兜底并发）。
     *
     * @param bookingId 订单 ID
     * @param command   目标动作
     * @throws BusinessException 订单当前状态不允许该推进
     */
    @Transactional
    public void advanceForFulfillment(long bookingId, FulfillmentAdvance command) {
        int updated = switch (command) {
            case DEPART -> orderMapper.markEnRoute(bookingId);
            case START -> orderMapper.startService(bookingId);
            case REQUEST_COMPLETE -> orderMapper.markPendingConfirm(bookingId);
            case COMPLETE -> orderMapper.complete(bookingId);
            case RESUME -> orderMapper.resumeInService(bookingId);
            case FORCE_COMPLETE -> orderMapper.forceComplete(bookingId);
        };
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BOOKING_STATUS_CONFLICT);
        }
        // 摘要列与主状态同步（updateById 只更新非 null 字段）
        BookingOrderEntity patch = new BookingOrderEntity();
        patch.setId(bookingId);
        patch.setFulfillmentStatus(switch (command) {
            case DEPART -> "EN_ROUTE";
            case START, RESUME -> "IN_SERVICE";
            case REQUEST_COMPLETE -> "PENDING_CONFIRM";
            case COMPLETE, FORCE_COMPLETE -> "COMPLETED";
        });
        orderMapper.updateById(patch);
        // 完成是订单状态机的终态转移之一：发完成事件供评价提醒/审计等消费（事务内落 outbox）
        if (command == FulfillmentAdvance.COMPLETE || command == FulfillmentAdvance.FORCE_COMPLETE) {
            BookingOrderEntity completed = orderMapper.selectById(bookingId);
            eventPublisher.publishCompleted(completed, command == FulfillmentAdvance.FORCE_COMPLETE);
        }
    }

    /**
     * 只同步履约摘要列（不推进订单主状态的履约动作，如签到）。
     *
     * @param bookingId        订单 ID
     * @param fulfillmentStatus 履约摘要值
     */
    @Transactional
    public void syncFulfillmentSummary(long bookingId, String fulfillmentStatus) {
        BookingOrderEntity patch = new BookingOrderEntity();
        patch.setId(bookingId);
        patch.setFulfillmentStatus(fulfillmentStatus);
        orderMapper.updateById(patch);
    }

    /** 履约侧允许的订单主状态推进动作（docs/modules/09 第 5.3 节映射表）。 */
    public enum FulfillmentAdvance {
        /** 出发：CONFIRMED → EN_ROUTE。 */
        DEPART,
        /** 开始服务：EN_ROUTE → IN_SERVICE。 */
        START,
        /** 申请完成：IN_SERVICE → PENDING_CONFIRM。 */
        REQUEST_COMPLETE,
        /** 确认完成：PENDING_CONFIRM → COMPLETED。 */
        COMPLETE,
        /** 异常恢复：EN_ROUTE / IN_SERVICE → IN_SERVICE。 */
        RESUME,
        /** 运营强制完成：EN_ROUTE / IN_SERVICE / PENDING_CONFIRM → COMPLETED。 */
        FORCE_COMPLETE
    }
}
