package com.housekeeping.platform.location.domain;

/**
 * 行政区层级（docs/modules/03 第 5.1 节）。
 *
 * <p>首版只支持四级，不做到社区/小区：小区级需要独立的 community 表与多边形边界，
 * 而首版匹配策略是"行政区编码 + 覆盖级别"，多一级只会让规则变复杂而不增加准确性。</p>
 */
public enum RegionLevel {

    PROVINCE,

    CITY,

    DISTRICT,

    STREET;

    /**
     * 是否为城市层级（服务区域必须挂在城市下）。
     *
     * @return 城市层级返回 {@code true}
     */
    public boolean isCity() {
        return this == CITY;
    }

    /**
     * 按编码解析层级。
     *
     * @param code 层级编码
     * @return 层级；编码非法时为空
     */
    public static java.util.Optional<RegionLevel> of(String code) {
        return java.util.Arrays.stream(values()).filter(level -> level.name().equals(code)).findFirst();
    }
}
