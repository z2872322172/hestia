package com.housekeeping.platform.location.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 行政区导入项请求。
 *
 * @param regionCode  行政区编码
 * @param parentCode  上级行政区编码，省级为空
 * @param regionLevel 层级
 * @param regionName  名称
 * @param shortName   简称
 * @param sortOrder   排序号
 */
public record RegionImportRequest(
        @NotBlank(message = "行政区编码不能为空") @Size(max = 12, message = "行政区编码过长") String regionCode,
        @Size(max = 12, message = "上级编码过长") String parentCode,
        @NotBlank(message = "行政区层级不能为空") String regionLevel,
        @NotBlank(message = "行政区名称不能为空") @Size(max = 128, message = "名称过长") String regionName,
        @Size(max = 64, message = "简称过长") String shortName,
        Integer sortOrder) {
}
