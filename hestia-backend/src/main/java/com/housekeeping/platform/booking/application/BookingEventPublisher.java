package com.housekeeping.platform.booking.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.housekeeping.platform.booking.domain.BookingEventTypes;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.messaging.EventOutbox;
import com.housekeeping.platform.infrastructure.messaging.OutboxEventWriter;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.RocketMqProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单事件发布器（docs/04 第 10.4/10.5 节）：订单状态机关键转移点写 Transactional Outbox。
 *
 * <p>使用铁律：所有 {@code publish*} 方法<b>必须在业务事务内调用</b>——
 * {@link OutboxEventWriter#append} 以 REQUIRED 传播加入当前事务，事件与业务变更
 * 同生共死；投递由 OutboxRelayJob 异步完成，MQ 不可用只影响投递不阻断业务。
 * 任何在事务外"补发"的调用都是双写，禁止。</p>
 *
 * <p>载荷只放消费所需的最小快照（docs/04 第 10.4 节：不放完整地址、证件号、支付密钥）。</p>
 */
@Service
@RequiresPersistence
public class BookingEventPublisher {

    private final OutboxEventWriter outboxEventWriter;

    private final RocketMqProperties rocketMqProperties;

    public BookingEventPublisher(OutboxEventWriter outboxEventWriter,
                                 RocketMqProperties rocketMqProperties) {
        this.outboxEventWriter = outboxEventWriter;
        this.rocketMqProperties = rocketMqProperties;
    }

    /**
     * 下单成功事件（事务内调用）。
     *
     * @param order 已插入的订单实体（PENDING_PAYMENT）
     */
    public void publishCreated(BookingOrderEntity order) {
        append(BookingEventTypes.BOOKING_CREATED, order.getOrderNo(), basePayload(order));
    }

    /**
     * 支付成功事件（事务内调用）。
     *
     * @param order      支付前查出的订单实体（状态仍是 PENDING_PAYMENT）
     * @param paidAmount 实付金额
     * @param paidAt     支付时间
     * @param workerId   派单激活后的服务人员 ID，未激活传 0
     */
    public void publishPaid(BookingOrderEntity order, BigDecimal paidAmount,
                            LocalDateTime paidAt, long workerId) {
        Map<String, Object> payload = basePayload(order);
        payload.put("status", "CONFIRMED");
        payload.put("paidAmount", paidAmount);
        payload.put("paidAt", paidAt);
        payload.put("currentWorkerId", workerId > 0 ? workerId : null);
        append(BookingEventTypes.BOOKING_PAID, order.getOrderNo(), payload);
    }

    /**
     * 改期成功事件（事务内调用）。
     *
     * @param order            改期前的订单实体（旧日期旧时段，用于记录原值）
     * @param newDate          新服务日期
     * @param newWindowCode    新时间窗编码
     * @param newWorkerId      新时段锁定的服务人员 ID
     */
    public void publishRescheduled(BookingOrderEntity order, LocalDate newDate,
                                   String newWindowCode, long newWorkerId) {
        Map<String, Object> payload = basePayload(order);
        payload.put("previousDate", order.getAppointmentDate());
        payload.put("previousStart", order.getAppointmentStart());
        payload.put("newDate", newDate);
        payload.put("newWindowCode", newWindowCode);
        payload.put("newWorkerId", newWorkerId);
        append(BookingEventTypes.BOOKING_RESCHEDULED, order.getOrderNo(), payload);
    }

    /**
     * 取消成功事件（事务内调用）。客户取消与支付超时关单共用。
     *
     * @param order      取消前查出的订单实体
     * @param reasonCode 取消原因编码，可空
     * @param reasonText 取消原因描述，可空
     * @param source     触发方：CUSTOMER / SYSTEM
     */
    public void publishCanceled(BookingOrderEntity order, String reasonCode,
                                String reasonText, String source) {
        Map<String, Object> payload = basePayload(order);
        payload.put("status", "CANCELED");
        if (reasonCode != null) {
            payload.put("reasonCode", reasonCode);
        }
        if (reasonText != null) {
            payload.put("reasonText", reasonText);
        }
        payload.put("source", source);
        append(BookingEventTypes.BOOKING_CANCELED, order.getOrderNo(), payload);
    }

    /**
     * 服务完成事件（事务内调用）。
     *
     * @param order 完成后的订单实体（状态 COMPLETED）
     * @param forced 是否运营强制完成
     */
    public void publishCompleted(BookingOrderEntity order, boolean forced) {
        Map<String, Object> payload = basePayload(order);
        payload.put("forced", forced);
        append(BookingEventTypes.BOOKING_COMPLETED, order.getOrderNo(), payload);
    }

    /** 所有事件共用的最小快照：单号、客户、状态、预约时间与金额。 */
    private Map<String, Object> basePayload(BookingOrderEntity order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("customerId", order.getCustomerId());
        payload.put("status", order.getOrderStatus());
        payload.put("appointmentDate", order.getAppointmentDate());
        payload.put("appointmentStart", order.getAppointmentStart());
        payload.put("payableAmount", order.getPayableAmount());
        return payload;
    }

    /**
     * 统一出口：eventType 即消息 Tag，messageKey 取订单号保证同单事件有序。
     * 载荷是本类手工组装的 Map，序列化失败只能是编码错误，转运行时异常让业务事务一起回滚。
     */
    private EventOutbox append(String eventType, String orderNo, Map<String, Object> payload) {
        try {
            return outboxEventWriter.append(eventType, BookingEventTypes.AGGREGATE_BOOKING_ORDER,
                    orderNo, rocketMqProperties.getTopics().getBookingEvent(),
                    eventType, orderNo, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单事件载荷序列化失败 eventType=" + eventType, e);
        }
    }
}
