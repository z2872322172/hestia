package com.housekeeping.platform.booking.application.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图（客户/人员/运营共用）。
 *
 * <p>地址与联系信息取自下单快照列，人员任务页与运营监控直接可用。</p>
 */
public record BookingDetailView(String orderNo,
                                Long customerId,
                                String customerNo,
                                String orderStatus,
                                String paymentStatus,
                                String assignmentStatus,
                                String fulfillmentStatus,
                                String afterSaleStatus,
                                String reviewStatus,
                                String serviceItemNo,
                                String serviceName,
                                long serviceItemVersion,
                                Long serviceAreaId,
                                String serviceAreaName,
                                LocalDate appointmentDate,
                                String serviceWindowName,
                                LocalDateTime appointmentStart,
                                LocalDateTime appointmentEnd,
                                Long currentWorkerId,
                                Long workerSlotId,
                                BigDecimal serviceAmount,
                                BigDecimal addonAmount,
                                BigDecimal materialAmount,
                                BigDecimal travelFee,
                                BigDecimal discountAmount,
                                BigDecimal payableAmount,
                                BigDecimal paidAmount,
                                BigDecimal refundedAmount,
                                String currency,
                                String contactName,
                                String contactPhone,
                                String detailAddress,
                                String cancelReasonCode,
                                String cancelReasonText,
                                LocalDateTime paymentDeadline,
                                String source,
                                List<BookingItemView> items,
                                LocalDateTime createdAt,
                                LocalDateTime paidAt,
                                LocalDateTime canceledAt) {
}
