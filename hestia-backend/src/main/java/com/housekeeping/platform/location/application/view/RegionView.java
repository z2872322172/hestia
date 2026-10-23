package com.housekeeping.platform.location.application.view;

/**
 * 行政区展示视图。
 *
 * @param id          主键
 * @param regionCode  行政区编码
 * @param parentCode  上级行政区编码，顶级为 {@code null}
 * @param regionLevel 层级
 * @param regionName  名称
 * @param status      状态
 */
public record RegionView(Long id, String regionCode, String parentCode, String regionLevel,
                         String regionName, String status) {
}
