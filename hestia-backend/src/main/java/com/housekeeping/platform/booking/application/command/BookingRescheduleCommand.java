package com.housekeeping.platform.booking.application.command;

import java.time.LocalDate;

/**
 * 改期命令。
 *
 * @param appointmentDate   新服务日期
 * @param serviceWindowCode 新服务时间窗编码
 */
public record BookingRescheduleCommand(LocalDate appointmentDate, String serviceWindowCode) {
}
