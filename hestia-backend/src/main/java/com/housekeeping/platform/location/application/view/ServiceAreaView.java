package com.housekeeping.platform.location.application.view;

import java.time.LocalDateTime;

/**
 * 服务区域视图（运营后台）。
 *
 * @param areaNo        编号
 * @param areaName      名称
 * @param description   说明
 * @param cityCode      城市编码
 * @param cityName      城市名称
 * @param areaType      类型
 * @param priority      优先级
 * @param status        状态
 * @param effectiveFrom 生效开始时间
 * @param effectiveTo   生效结束时间
 * @param version       乐观锁版本
 */
public record ServiceAreaView(String areaNo, String areaName, String description,
                              String cityCode, String cityName, String areaType,
                              Integer priority, String status,
                              LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                              Long version) {
}
