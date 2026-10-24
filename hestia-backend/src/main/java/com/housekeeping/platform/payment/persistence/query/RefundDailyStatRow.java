package com.housekeeping.platform.payment.persistence.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 退款金额按日统计行（运营统计契约的 SQL 映射行，docs/modules/13 第 7.1 节）。
 *
 * <p>统计口径：按退款完成日（refunded_at）归属，仅统计 SUCCESS 退款。</p>
 */
@Data
public class RefundDailyStatRow {

    /** 统计日期（取 refunded_at 的日期部分）。 */
    private LocalDate statDate;

    /** 服务区域 ID（联订单表获得）。 */
    private Long serviceAreaId;

    /** 退款完成笔数。 */
    private Long cnt;

    /** 退款金额合计。 */
    private BigDecimal amount;
}
