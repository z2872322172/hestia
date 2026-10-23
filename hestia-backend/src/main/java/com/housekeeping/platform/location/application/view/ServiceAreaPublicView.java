package com.housekeeping.platform.location.application.view;

/**
 * 服务区域公开视图（消费者可见）。
 *
 * <p>刻意不含 {@code priority}、{@code status}、覆盖明细：
 * 这些是经营配置，公开后等于把派单与定价策略交给外部。</p>
 *
 * @param areaNo      编号
 * @param areaName    名称
 * @param description 说明
 */
public record ServiceAreaPublicView(String areaNo, String areaName, String description) {
}
