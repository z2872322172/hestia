package com.housekeeping.platform.booking.application.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单列表项视图（客户/人员/运营列表共用）。
 *
 * @param orderNo          订单号
 * @param serviceName      服务名称快照
 * @param orderStatus      主状态
 * @param paymentStatus    支付摘要状态
 * @param appointmentDate  服务日期
 * @param appointmentStart 服务开始时间
 * @param payableAmount    应付金额
 * @param createdAt        下单时间
 */
public record BookingSummaryView(String orderNo, String serviceName, String orderStatus,
                                 String paymentStatus, LocalDate appointmentDate,
                                 LocalDateTime appointmentStart, BigDecimal payableAmount,
                                 LocalDateTime createdAt) {
}
