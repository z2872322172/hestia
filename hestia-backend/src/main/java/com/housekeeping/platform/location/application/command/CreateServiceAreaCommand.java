package com.housekeeping.platform.location.application.command;

import java.time.LocalDateTime;

/**
 * 创建服务区域命令。
 *
 * @param areaName      名称，必填
 * @param description   说明
 * @param cityCode      城市行政区编码，必填且必须是 CITY 层级
 * @param areaType      DISTRICT_GROUP / STREET_GROUP / CUSTOM，为空时按 DISTRICT_GROUP
 * @param priority      优先级，0 至 999，越大越优先
 * @param effectiveFrom 生效开始时间，可为空
 * @param effectiveTo   生效结束时间，可为空
 */
public record CreateServiceAreaCommand(String areaName, String description, String cityCode,
                                       String areaType, Integer priority,
                                       LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
}
