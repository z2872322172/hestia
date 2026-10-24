package com.housekeeping.platform.booking.domain;

/**
 * 订单领域事件类型常量（docs/04 第 10.4 节事件信封示例的订单族）。
 *
 * <p>事件只作"通知"用途：订单状态流转的一致性由各业务事务内的同步调用保证，
 * 事件是给审计、外部集成与未来消费者准备的可靠副本，因此只覆盖订单状态机的
 * 五个关键转移点，不追求每个字段变更都发事件。</p>
 */
public final class BookingEventTypes {

    /** 下单成功：订单进入 PENDING_PAYMENT，时段已冻结。 */
    public static final String BOOKING_CREATED = "BOOKING_CREATED";

    /** 支付成功：订单确认（PENDING_PAYMENT → CONFIRMED），派单任务激活。 */
    public static final String BOOKING_PAID = "BOOKING_PAID";

    /** 改期成功：时段与派单已切换到新的日期/时间窗。 */
    public static final String BOOKING_RESCHEDULED = "BOOKING_RESCHEDULED";

    /** 取消成功：客户取消或支付超时自动关单（payload.source 区分）。 */
    public static final String BOOKING_CANCELED = "BOOKING_CANCELED";

    /** 服务完成：正常确认完成或运营强制完成（payload.forced 区分）。 */
    public static final String BOOKING_COMPLETED = "BOOKING_COMPLETED";

    /** 订单事件的聚合类型（信封 aggregateType）。 */
    public static final String AGGREGATE_BOOKING_ORDER = "BOOKING_ORDER";

    private BookingEventTypes() {
    }
}
