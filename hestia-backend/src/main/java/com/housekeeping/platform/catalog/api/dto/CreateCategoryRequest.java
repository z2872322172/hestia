package com.housekeeping.platform.catalog.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建服务分类请求。
 *
 * @param parentNo    父分类编号，为空表示一级分类
 * @param categoryName 分类名称
 * @param iconUrl      图标
 * @param description  描述
 * @param sortOrder    排序值
 */
public record CreateCategoryRequest(
        @Size(max = 32, message = "父分类编号过长") String parentNo,
        @NotBlank(message = "分类名称不能为空") @Size(max = 64, message = "分类名称过长") String categoryName,
        @Size(max = 512, message = "图标地址过长") String iconUrl,
        @Size(max = 255, message = "描述过长") String description,
        @Min(value = 0, message = "排序值不能为负") Integer sortOrder) {
}
