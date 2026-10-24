package com.housekeeping.platform.booking.persistence.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单导出行（docs/modules/13 第 11 节）。
 *
 * <p>只包含报表必需字段；联系人姓名 / 电话 / 详址等隐私字段不进入导出文件。</p>
 */
@Data
public class BookingExportRow {

    /** 订单号。 */
    private String orderNo;

    /** 服务区域 ID。 */
    private Long serviceAreaId;

    /** 服务名称快照。 */
    private String serviceName;

    /** 主状态。 */
    private String orderStatus;

    /** 支付状态。 */
    private String paymentStatus;

    /** 服务日期。 */
    private LocalDate appointmentDate;

    /** 应付金额。 */
    private BigDecimal payableAmount;

    /** 实付金额。 */
    private BigDecimal paidAmount;

    /** 已退金额。 */
    private BigDecimal refundedAmount;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
