package com.housekeeping.platform.scheduling.application.command;

import java.time.LocalTime;

/**
 * 新建标准时间窗命令。
 *
 * @param windowCode 时间窗编码（稳定业务键，如 MORNING）
 * @param windowName 展示名称
 * @param startTime  开始时间
 * @param endTime    结束时间（必须晚于开始时间）
 * @param sortOrder  展示排序
 */
public record CreateServiceWindowCommand(String windowCode, String windowName,
                                         LocalTime startTime, LocalTime endTime,
                                         Integer sortOrder) {
}
