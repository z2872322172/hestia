package com.housekeeping.platform.booking.application;

import com.housekeeping.platform.booking.domain.BookingStatus;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingStatusLogEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingStatusLogMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.dispatch.application.DispatchApplicationService;
import com.housekeeping.platform.payment.application.PaymentApplicationService;
import com.housekeeping.platform.scheduling.application.WorkerSlotReservationService;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerSlotEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时自动取消任务（docs/modules/07 第 10.1 节）。
 *
 * <p>扫描支付截止时间已过仍处于 PENDING_PAYMENT 的订单，逐单执行与
 * "待支付取消"相同的链路（条件更新取消 → 释放时段 → 取消派单 → 日志）。
 * 单单容错：一个订单的异常不阻断同批其他订单。</p>
 *
 * <p>条件更新是并发安全的兜底：客户恰好同时取消或支付时，
 * 本任务的 {@code UPDATE ... WHERE order_status = 'PENDING_PAYMENT'} 影响行数为 0，
 * 不会重复释放时段或写重复日志。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class BookingPaymentTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(BookingPaymentTimeoutJob.class);

    private static final String REASON_CODE = "PAYMENT_TIMEOUT";

    private static final String REASON_TEXT = "超时未支付，系统自动取消";

    private final BookingOrderMapper orderMapper;

    private final BookingStatusLogMapper statusLogMapper;

    private final WorkerSlotReservationService slotReservation;

    private final DispatchApplicationService dispatchService;

    private final PaymentApplicationService paymentService;

    private final BookingProperties properties;

    private final BookingEventPublisher eventPublisher;

    public BookingPaymentTimeoutJob(BookingOrderMapper orderMapper,
                                    BookingStatusLogMapper statusLogMapper,
                                    WorkerSlotReservationService slotReservation,
                                    DispatchApplicationService dispatchService,
                                    PaymentApplicationService paymentService,
                                    BookingProperties properties,
                                    BookingEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.slotReservation = slotReservation;
        this.dispatchService = dispatchService;
        this.paymentService = paymentService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 定时扫描；间隔由 {@code app.booking.timeout-sweep-interval-ms} 配置。
     */
    @Scheduled(fixedDelayString = "${app.booking.timeout-sweep-interval-ms:30000}",
            initialDelayString = "${app.booking.timeout-sweep-interval-ms:30000}")
    public void scheduledSweep() {
        int handled = runOnce();
        if (handled > 0) {
            log.info("booking payment timeout sweep handled {} order(s)", handled);
        }
    }

    /**
     * 执行一轮扫描（冒烟脚本手动触发复用）。
     *
     * @return 本轮处理的订单数
     */
    public int runOnce() {
        return runOnce(LocalDateTime.now());
    }

    /**
     * 以指定时刻执行一轮扫描（冒烟脚本注入模拟时钟，无需真实等待超时）。
     *
     * @param asOf 视作当前的时刻
     * @return 本轮处理的订单数
     */
    public int runOnce(LocalDateTime asOf) {
        List<BookingOrderEntity> expired = orderMapper.selectTimeoutPending(
                asOf, properties.getTimeoutSweepBatchSize());
        int handled = 0;
        for (BookingOrderEntity order : expired) {
            try {
                if (cancelOne(order)) {
                    handled++;
                }
            } catch (Exception exception) {
                // 单单容错：下一轮扫描会重试仍处于 PENDING_PAYMENT 的订单
                log.warn("booking payment timeout cancel failed, orderNo={}",
                        order.getOrderNo(), exception);
            }
        }
        return handled;
    }

    private boolean cancelOne(BookingOrderEntity order) {
        if (orderMapper.cancel(order.getId(), BookingStatus.PENDING_PAYMENT.name(),
                REASON_CODE, REASON_TEXT) == 0) {
            // 状态已被客户取消或支付确认抢先推进，本单无需处理
            return false;
        }
        WorkerSlotEntity slot = slotReservation.findByBooking(order.getId());
        if (slot != null) {
            slotReservation.releaseHeld(slot.getId(), order.getId());
        }
        dispatchService.cancelForBooking(order.getId());
        // 未完成的支付单一并关闭，防止客户在订单取消后还能对旧支付单回调成功
        paymentService.closeForBooking(order.getId());
        // 摘要列与派单表同步：取消后运营监控应立即看到 CANCELED
        BookingOrderEntity patch = new BookingOrderEntity();
        patch.setId(order.getId());
        patch.setAssignmentStatus("CANCELED");
        orderMapper.updateById(patch);
        BookingStatusLogEntity logEntity = new BookingStatusLogEntity();
        logEntity.setBookingId(order.getId());
        logEntity.setFromStatus(BookingStatus.PENDING_PAYMENT.name());
        logEntity.setToStatus(BookingStatus.CANCELED.name());
        logEntity.setOperationSource("SYSTEM");
        logEntity.setReasonCode(REASON_CODE);
        logEntity.setReasonText(REASON_TEXT);
        statusLogMapper.insert(logEntity);
        // 超时关单也是订单取消事件，source=SYSTEM 与客户取消区分（事务内落 outbox）
        eventPublisher.publishCanceled(order, REASON_CODE, REASON_TEXT, "SYSTEM");
        return true;
    }
}
