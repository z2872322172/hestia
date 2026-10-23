package com.housekeeping.platform.catalog.domain;

/**
 * 基础销售单位（docs/modules/04 第 6.3 节）。
 *
 * <p>单位决定"数量"的语义：按次卖数量恒为 1，按平米卖数量是面积，按小时卖数量是时长。
 * 单位与计价模式是两件事——按平米卖也可以有固定价（面积档位固定价），
 * 所以不把两者合并成一个枚举。</p>
 */
public enum ServiceUnit {

    /** 按单次服务计价。 */
    ORDER,

    /** 按小时计价。 */
    HOUR,

    /** 按建筑面积计价。 */
    SQUARE_METER,

    /** 按件数计价。 */
    ITEM;

    /**
     * 解析字符串，非法值抛 {@link IllegalArgumentException}。
     *
     * @param value 字符串
     * @return 枚举
     */
    public static ServiceUnit of(String value) {
        for (ServiceUnit unit : values()) {
            if (unit.name().equals(value)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("未知服务单位: " + value);
    }
}
