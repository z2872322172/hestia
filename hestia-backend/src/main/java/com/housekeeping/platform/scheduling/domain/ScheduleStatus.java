package com.housekeeping.platform.scheduling.domain;

/**
 * 排班记录状态（docs/modules/06 第 6.1 节）。
 *
 * <p>取消不是删除：排班取消后，未占用时段转 BLOCKED/CLOSED，
 * 已占用时段必须走改派流程，历史数据通过状态而非物理删除保留。</p>
 */
public enum ScheduleStatus {

    /** 生效中。 */
    ACTIVE,

    /** 已取消（计划撤销，记录保留）。 */
    CANCELED;

    /**
     * 解析排班状态字符串。
     *
     * @param value 数据库存量值
     * @return 排班状态
     */
    public static ScheduleStatus of(String value) {
        return ScheduleStatus.valueOf(value);
    }
}
