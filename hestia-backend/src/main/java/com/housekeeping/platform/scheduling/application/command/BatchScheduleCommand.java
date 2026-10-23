package com.housekeeping.platform.scheduling.application.command;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量创建排班命令。
 *
 * <p>运营端 {@code workerNo} 必填；人员自助端不传（本人只能给自己排班，
 * 且只允许 WORK 类型——请假走独立的请假入口）。</p>
 *
 * @param workerNo     人员编号（自助端为 {@code null}）
 * @param serviceDates 服务日期集合
 * @param windowIds    时间窗 ID 集合
 * @param scheduleType 排班类型（WORK / LEAVE / TRAINING / REST）
 * @param note         备注（请假原因等）
 */
public record BatchScheduleCommand(String workerNo, List<LocalDate> serviceDates,
                                   List<Long> windowIds, String scheduleType, String note) {
}
