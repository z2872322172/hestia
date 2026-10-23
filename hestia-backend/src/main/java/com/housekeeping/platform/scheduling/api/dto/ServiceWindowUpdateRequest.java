package com.housekeeping.platform.scheduling.api.dto;

import com.housekeeping.platform.scheduling.application.command.UpdateServiceWindowCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * 修改标准时间窗请求（运营端）。
 *
 * <p>修改只影响后续生成的时段，存量排班与时段不回溯
 * （docs/modules/06 第 19.2 节）。{@code expectedVersion} 不匹配即并发冲突。</p>
 *
 * @param windowName      展示名称，可空表示不修改
 * @param startTime       开始时间
 * @param endTime         结束时间
 * @param sortOrder       展示排序，可空表示不修改
 * @param status          ACTIVE / DISABLED，其他值按 ACTIVE 处理
 * @param expectedVersion 乐观锁版本
 */
public record ServiceWindowUpdateRequest(
        @Size(max = 64, message = "时间窗名称过长") String windowName,
        LocalTime startTime,
        LocalTime endTime,
        Integer sortOrder,
        String status,
        @NotNull(message = "版本号不能为空") Long expectedVersion) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public UpdateServiceWindowCommand toCommand() {
        return new UpdateServiceWindowCommand(windowName, startTime, endTime, sortOrder, status,
                expectedVersion);
    }
}
