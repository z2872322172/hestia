package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 切换活动角色请求。
 *
 * @param role 目标角色编码
 */
public record SwitchRoleRequest(
        @NotBlank(message = "目标角色不能为空") @Size(max = 32, message = "角色编码过长") String role) {
}
