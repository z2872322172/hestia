package com.housekeeping.platform.booking.persistence.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 支付金额按日统计行（运营统计契约的 SQL 映射行）。
 *
 * <p>统计口径：按支付完成日（paid_at）归属，金额取订单实付列。</p>
 */
@Data
public class BookingPaidStatRow {

    /** 统计日期（取 paid_at 的日期部分）。 */
    private LocalDate statDate;

    /** 服务区域 ID。 */
    private Long serviceAreaId;

    /** 支付完成订单数。 */
    private Long cnt;

    /** 支付金额合计。 */
    private BigDecimal amount;
}
