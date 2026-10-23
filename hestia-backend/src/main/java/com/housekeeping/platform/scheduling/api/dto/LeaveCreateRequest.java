package com.housekeeping.platform.scheduling.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 人员自助请假请求。
 *
 * <p>请假至少提前一天：当天临时请假走线下沟通与运营端调整
 * （docs/modules/06 第 4.2 节）。若该日期时间窗已有排班（如已报 WORK），
 * 唯一约束会拒绝——先取消工作排班再请假。</p>
 *
 * @param serviceDate 请假日期
 * @param windowId    时间窗 ID
 * @param note        请假原因
 */
public record LeaveCreateRequest(
        @NotNull(message = "请假日期不能为空") LocalDate serviceDate,
        @NotNull(message = "时间窗不能为空") Long windowId,
        @Size(max = 255, message = "请假原因过长") String note) {
}
