package com.housekeeping.platform.location.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建服务区域请求。
 *
 * @param areaName      名称
 * @param description   说明
 * @param cityCode      城市行政区编码
 * @param areaType      类型
 * @param priority      优先级
 * @param effectiveFrom 生效开始时间
 * @param effectiveTo   生效结束时间
 */
public record CreateServiceAreaRequest(
        @NotBlank(message = "服务区域名称不能为空") @Size(max = 128, message = "名称过长") String areaName,
        @Size(max = 500, message = "说明过长") String description,
        @NotBlank(message = "城市编码不能为空") String cityCode,
        @Size(max = 32, message = "类型过长") String areaType,
        @Min(value = 0, message = "优先级不能小于 0") @Max(value = 999, message = "优先级不能大于 999") Integer priority,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {
}
