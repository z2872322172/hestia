package com.housekeeping.platform.location.application.view;

/**
 * 覆盖范围视图。
 *
 * @param id            覆盖记录 ID
 * @param regionCode    行政区编码
 * @param regionName    行政区名称
 * @param coverageLevel 覆盖级别
 * @param status        覆盖状态
 */
public record CoverageView(Long id, String regionCode, String regionName,
                           String coverageLevel, String status) {
}
