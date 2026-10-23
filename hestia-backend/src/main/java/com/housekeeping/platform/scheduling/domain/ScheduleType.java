package com.housekeeping.platform.scheduling.domain;

/**
 * 排班类型（docs/modules/06 第 6.1 节）。
 *
 * <p>只有 {@link #WORK} 生成可预约（AVAILABLE）时段；
 * 请假、培训、休息生成 BLOCKED 时段——"这个人那天在，但不能接单"。</p>
 */
public enum ScheduleType {

    /** 工作：生成 AVAILABLE 时段。 */
    WORK,

    /** 请假：生成 BLOCKED 时段。 */
    LEAVE,

    /** 培训：生成 BLOCKED 时段。 */
    TRAINING,

    /** 休息：生成 BLOCKED 时段。 */
    REST;

    /**
     * 该类型的排班是否生成可预约时段。
     *
     * @return {@code WORK} 返回 {@code true}
     */
    public boolean generatesBookableSlot() {
        return this == WORK;
    }

    /**
     * 该类型的排班是否生成被阻塞时段。
     *
     * @return 非工作类型返回 {@code true}
     */
    public boolean generatesBlockedSlot() {
        return !generatesBookableSlot();
    }

    /**
     * 解析排班类型字符串。
     *
     * @param value 数据库存量值
     * @return 排班类型
     * @throws IllegalArgumentException 未知取值（说明库内出现脏数据，直接失败而不是静默降级）
     */
    public static ScheduleType of(String value) {
        return ScheduleType.valueOf(value);
    }
}
