package com.housekeeping.platform.booking.persistence.query;

import lombok.Data;

/**
 * 运营看板"今日"计数行（SQL 映射行）。
 *
 * <p>SUM 布尔在空结果集下返回 NULL，因此用 Long 承接，由应用层兜 0。</p>
 */
@Data
public class BookingTodayStatRow {

    /** 今日创建订单数。 */
    private Long total;

    /** 今日创建且当前仍待支付的订单数。 */
    private Long pendingPayment;

    /** 今日创建且已完成的订单数。 */
    private Long completed;

    /** 今日创建且已取消的订单数。 */
    private Long canceled;
}
