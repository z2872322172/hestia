package com.housekeeping.platform.workforce.domain;

/**
 * 技能等级（docs/modules/05 第 7.1 节）。
 *
 * <p>等级只用于派单排序时的偏好，<b>不</b>参与"能不能干"的判定：
 * 一个 JUNIOR 只要技能审核通过，就有资格接该服务项目的单。
 * 把等级写进资格门槛会让新人永远接不到第一单，而这正是家政行业留不住人的原因。</p>
 */
public enum SkillLevel {

    /** 初级。 */
    JUNIOR,

    /** 中级。 */
    INTERMEDIATE,

    /** 高级。 */
    SENIOR;

    /**
     * 解析技能等级。
     *
     * @param value 字符串
     * @return 枚举值
     * @throws IllegalArgumentException 取值非法
     */
    public static SkillLevel of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("技能等级不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("技能等级非法：" + value);
        }
    }
}
