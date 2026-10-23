package com.housekeeping.platform.scheduling.application.view;

import java.time.LocalTime;

/**
 * 标准时间窗视图。
 *
 * @param windowId   时间窗 ID
 * @param windowCode 时间窗编码
 * @param windowName 展示名称
 * @param startTime  开始时间
 * @param endTime    结束时间
 * @param sortOrder  排序
 * @param status     ACTIVE / DISABLED
 * @param version    乐观锁版本（运营修改用）
 */
public record ServiceWindowView(long windowId, String windowCode, String windowName,
                                LocalTime startTime, LocalTime endTime, Integer sortOrder,
                                String status, long version) {

    /**
     * 时间窗是否可用。
     *
     * @return {@code ACTIVE} 返回 {@code true}
     */
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
