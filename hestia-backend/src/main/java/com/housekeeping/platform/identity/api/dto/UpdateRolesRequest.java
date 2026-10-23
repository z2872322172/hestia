package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 调整账号角色请求。
 *
 * @param roles 目标角色编码（整体覆盖）
 */
public record UpdateRolesRequest(
        @NotEmpty(message = "至少需要一个角色") List<@NotBlank @Size(max = 32) String> roles) {
}
