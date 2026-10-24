package com.housekeeping.platform.notification.domain;

/**
 * 通知模块消费的领域事件类型（docs/modules/12 第 5 节的 MVP 子集）。
 *
 * <p>首版只接订单事件族（{@code HK_BOOKING_EVENT} Topic）：
 * 下单、支付、改期、取消、完成五个关键转移点，正好覆盖
 * "客户知道订单怎么样了"与"人员知道任务怎么样了"两条主线。
 * 派单族 / 履约族 / 售后族事件随对应模块接入后在此追加，
 * 未登记的事件类型消费后直接确认跳过，不会堆积死信。</p>
 */
public final class NotificationEventTypes {

    /** 下单成功（收件人：客户）。 */
    public static final String BOOKING_CREATED = "BOOKING_CREATED";

    /** 支付成功（收件人：客户 + 中标人员，人员侧即"收到新任务"）。 */
    public static final String BOOKING_PAID = "BOOKING_PAID";

    /** 改期成功（收件人：客户 + 当前人员）。 */
    public static final String BOOKING_RESCHEDULED = "BOOKING_RESCHEDULED";

    /** 取消成功，含支付超时自动关单（收件人：客户 + 当前人员，如有）。 */
    public static final String BOOKING_CANCELED = "BOOKING_CANCELED";

    /** 服务完成（收件人：客户 + 当前人员）。 */
    public static final String BOOKING_COMPLETED = "BOOKING_COMPLETED";

    private NotificationEventTypes() {
    }

    /**
     * 判断事件类型是否受支持。
     *
     * @param eventType 事件类型
     * @return true 表示通知模块会为其生成任务
     */
    public static boolean supports(String eventType) {
        return BOOKING_CREATED.equals(eventType)
                || BOOKING_PAID.equals(eventType)
                || BOOKING_RESCHEDULED.equals(eventType)
                || BOOKING_CANCELED.equals(eventType)
                || BOOKING_COMPLETED.equals(eventType);
    }
}
