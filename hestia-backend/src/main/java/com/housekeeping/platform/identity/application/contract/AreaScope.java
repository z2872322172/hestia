package com.housekeeping.platform.identity.application.contract;

import java.util.List;

/**
 * 账号数据范围（docs/modules/13 第 6 节）。
 *
 * <p>{@code allAreas = true} 表示可查看全部区域（ADMIN）；否则只允许查看
 * {@code areaIds} 列出的服务区域。<b>空列表 + 非全量 = 无任何业务数据</b>，
 * 这是"没有配置区域就看不到数据"原则的落点，调用方不得把空列表当成全量。</p>
 *
 * @param allAreas 是否可查看全部区域
 * @param areaIds  授权服务区域 ID 列表；{@code allAreas} 为 true 时无意义（可为空）
 */
public record AreaScope(boolean allAreas, List<Long> areaIds) {

    /**
     * 全量范围（ADMIN）。
     *
     * @return 全量范围实例
     */
    public static AreaScope all() {
        return new AreaScope(true, List.of());
    }

    /**
     * 限定区域范围（OPERATOR）。
     *
     * @param areaIds 授权区域，允许为空列表（表示看不到任何数据）
     * @return 限定范围实例
     */
    public static AreaScope ofAreas(List<Long> areaIds) {
        return new AreaScope(false, areaIds == null ? List.of() : List.copyOf(areaIds));
    }

    /**
     * 判断给定服务区域是否在范围内。
     *
     * @param serviceAreaId 服务区域 ID
     * @return 在范围内返回 {@code true}
     */
    public boolean covers(Long serviceAreaId) {
        if (allAreas) {
            return true;
        }
        return serviceAreaId != null && areaIds.contains(serviceAreaId);
    }
}
