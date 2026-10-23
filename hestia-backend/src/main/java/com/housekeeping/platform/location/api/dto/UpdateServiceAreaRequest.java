package com.housekeeping.platform.location.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 修改服务区域请求。
 *
 * @param areaName      名称
 * @param description   说明
 * @param priority      优先级
 * @param effectiveFrom 生效开始时间
 * @param effectiveTo   生效结束时间
 * @param version       客户端持有的版本号，用于乐观锁
 */
public record UpdateServiceAreaRequest(
        @NotBlank(message = "服务区域名称不能为空") @Size(max = 128, message = "名称过长") String areaName,
        @Size(max = 500, message = "说明过长") String description,
        @Min(value = 0, message = "优先级不能小于 0") @Max(value = 999, message = "优先级不能大于 999") Integer priority,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        @NotNull(message = "缺少版本号") Long version) {
}
