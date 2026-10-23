package com.housekeeping.platform.booking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 改期请求。
 *
 * @param appointmentDate   新服务日期（ISO：yyyy-MM-dd）
 * @param serviceWindowCode 新服务时间窗编码
 */
public record BookingRescheduleRequest(
        @NotNull LocalDate appointmentDate,
        @NotBlank String serviceWindowCode) {
}
