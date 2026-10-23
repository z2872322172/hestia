package com.housekeeping.platform.scheduling.api.dto;

import com.housekeeping.platform.scheduling.application.command.CreateServiceWindowCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * 新建标准时间窗请求（运营端）。
 *
 * @param windowCode 时间窗编码（稳定业务键，如 MORNING）
 * @param windowName 展示名称
 * @param startTime  开始时间，ISO 格式（HH:mm）
 * @param endTime    结束时间，必须晚于开始时间
 * @param sortOrder  展示排序，可空默认 0
 */
public record ServiceWindowRequest(
        @NotBlank(message = "时间窗编码不能为空")
        @Size(max = 32, message = "时间窗编码过长")
        @Pattern(regexp = "[A-Z0-9_]+", message = "时间窗编码只能包含大写字母、数字与下划线") String windowCode,
        @NotBlank(message = "时间窗名称不能为空")
        @Size(max = 64, message = "时间窗名称过长") String windowName,
        @NotNull(message = "开始时间不能为空") LocalTime startTime,
        @NotNull(message = "结束时间不能为空") LocalTime endTime,
        Integer sortOrder) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public CreateServiceWindowCommand toCommand() {
        return new CreateServiceWindowCommand(windowCode, windowName, startTime, endTime, sortOrder);
    }
}
