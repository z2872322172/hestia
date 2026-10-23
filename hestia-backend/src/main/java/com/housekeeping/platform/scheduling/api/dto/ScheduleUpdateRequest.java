package com.housekeeping.platform.scheduling.api.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 修改排班请求（运营端）。
 *
 * <p>只允许修改空闲（AVAILABLE）时段的排班；已锁定或已占用时段必须走
 * 改派或改期流程，绝不能被排班修改静默吞掉（docs/modules/06 第 12.2 节）。
 * 日期与时间窗可缺省，缺省表示保持不变。</p>
 *
 * @param serviceDate 新的服务日期，可空表示不修改
 * @param windowId    新的时间窗 ID，可空表示不修改
 * @param note        备注，可空表示不修改
 */
public record ScheduleUpdateRequest(
        LocalDate serviceDate,
        Long windowId,
        @Size(max = 255, message = "备注过长") String note) {
}
