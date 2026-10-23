package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 调整运营区域范围请求。
 *
 * @param roleCode        角色编码
 * @param serviceAreaIds  服务区域 ID 列表（整体覆盖，空列表表示收回全部区域）
 */
public record UpdateAreaScopesRequest(
        @NotBlank(message = "角色编码不能为空") @Size(max = 32, message = "角色编码过长") String roleCode,
        List<Long> serviceAreaIds) {
}
