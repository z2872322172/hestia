package com.housekeeping.platform.scheduling.application.view;

import java.util.List;

/**
 * 可用性聚合视图（docs/modules/06 第 8 节）。
 *
 * <p>只回答"某天某时间窗能不能约"，不返回剩余人数与人员名单——
 * 内部供给情况不暴露给消费者侧。</p>
 *
 * @param serviceDate 服务日期（ISO 格式 yyyy-MM-dd）
 * @param windows     该日期各时间窗的可用性，按展示排序
 */
public record AvailabilityView(String serviceDate, List<WindowAvailability> windows) {

    /**
     * 单个时间窗的可用性。
     *
     * @param windowCode 时间窗编码
     * @param windowName 时间窗名称
     * @param startTime  开始时间（HH:mm）
     * @param endTime    结束时间（HH:mm）
     * @param available  是否至少存在一名合格人员
     */
    public record WindowAvailability(String windowCode, String windowName,
                                     String startTime, String endTime, boolean available) {
    }
}
