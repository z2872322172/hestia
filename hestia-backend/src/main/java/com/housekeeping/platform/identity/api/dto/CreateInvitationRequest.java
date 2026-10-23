package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 服务人员邀请请求。
 *
 * @param countryCode 区号
 * @param phone       手机号
 * @param displayName 显示名称
 */
public record CreateInvitationRequest(
        @Size(max = 8, message = "区号过长") String countryCode,
        @NotBlank(message = "手机号不能为空") @Size(max = 20, message = "手机号过长") String phone,
        @NotBlank(message = "显示名称不能为空") @Size(max = 64, message = "显示名称过长") String displayName) {
}
