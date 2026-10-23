package com.housekeeping.platform.workforce.domain;

/**
 * 用工类型（docs/modules/05 第 6 节）。
 *
 * <p>全职与兼职共用同一套档案、技能、证书、区域和资格校验。
 * 差异只体现在排班生成方式上，因此本枚举<b>不</b>参与可派单资格判定——
 * 如果资格逻辑里出现 {@code if (PART_TIME)}，说明把"能不能干"和"什么时候能干"
 * 混成了一件事，兼职人员会莫名其妙地被判定为不可派单。</p>
 */
public enum EmploymentType {

    /** 全职。 */
    FULL_TIME,

    /** 兼职。 */
    PART_TIME;

    /**
     * 解析用工类型。
     *
     * @param value 字符串
     * @return 枚举值
     * @throws IllegalArgumentException 取值非法
     */
    public static EmploymentType of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("用工类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("用工类型非法：" + value);
        }
    }
}
