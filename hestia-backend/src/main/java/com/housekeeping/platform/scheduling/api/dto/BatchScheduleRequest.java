package com.housekeeping.platform.scheduling.api.dto;

import com.housekeeping.platform.scheduling.application.command.BatchScheduleCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量创建排班请求。
 *
 * <p>运营端 {@code workerNo} 必填；人员自助端不传（传了也会被忽略，
 * 本人只能给自己排班）。自助端只允许 {@code WORK} 类型，日期必须明天及以后。</p>
 *
 * @param workerNo     人员编号（自助端可空）
 * @param serviceDates 服务日期集合
 * @param windowIds    时间窗 ID 集合
 * @param scheduleType 排班类型：WORK / LEAVE / TRAINING / REST
 * @param note         备注（请假原因等）
 */
public record BatchScheduleRequest(
        @Size(max = 32, message = "人员编号过长") String workerNo,
        @NotEmpty(message = "服务日期不能为空") List<@NotNull LocalDate> serviceDates,
        @NotEmpty(message = "时间窗不能为空") List<@NotNull Long> windowIds,
        @NotNull(message = "排班类型不能为空") String scheduleType,
        @Size(max = 255, message = "备注过长") String note) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public BatchScheduleCommand toCommand() {
        return new BatchScheduleCommand(workerNo, serviceDates, windowIds, scheduleType, note);
    }
}
