package com.housekeeping.platform.scheduling.application.view;

/**
 * 排班记录视图（本人与运营共用）。
 *
 * @param scheduleId   排班 ID
 * @param workerNo     人员编号
 * @param serviceDate  服务日期
 * @param windowCode   时间窗编码
 * @param windowName   时间窗名称
 * @param scheduleType 排班类型
 * @param scheduleStatus 排班状态
 * @param slotStatus   时段状态（生成失败时为 {@code null}）
 * @param note         备注
 */
public record ScheduleView(long scheduleId, String workerNo, String serviceDate,
                           String windowCode, String windowName, String scheduleType,
                           String scheduleStatus, String slotStatus, String note) {
}
