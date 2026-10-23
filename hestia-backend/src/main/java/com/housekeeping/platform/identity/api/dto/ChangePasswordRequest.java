package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") @Size(max = 64, message = "原密码过长") String oldPassword,
        @NotBlank(message = "新密码不能为空") @Size(max = 64, message = "新密码过长") String newPassword) {
}
