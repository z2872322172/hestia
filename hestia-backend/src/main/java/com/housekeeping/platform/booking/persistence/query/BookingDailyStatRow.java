package com.housekeeping.platform.booking.persistence.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 订单按日统计行（运营统计契约的 SQL 映射行，docs/modules/13 第 7.1 节）。
 *
 * <p>统计口径：按订单创建日归属；completed / canceled 为该批订单当前终态。</p>
 */
@Data
public class BookingDailyStatRow {

    /** 统计日期（取 created_at 的日期部分）。 */
    private LocalDate statDate;

    /** 服务区域 ID。 */
    private Long serviceAreaId;

    /** 订单创建数。 */
    private Long total;

    /** 其中当前已完成的订单数（改期统计行不使用）。 */
    private Long completed;

    /** 其中当前已取消的订单数（改期统计行不使用）。 */
    private Long canceled;
}
