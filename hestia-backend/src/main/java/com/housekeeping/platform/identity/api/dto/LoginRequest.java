package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * @param countryCode 区号
 * @param phone       手机号
 * @param password    密码
 */
public record LoginRequest(
        @Size(max = 8, message = "区号过长") String countryCode,
        @NotBlank(message = "手机号不能为空") @Size(max = 20, message = "手机号过长") String phone,
        @NotBlank(message = "密码不能为空") @Size(max = 64, message = "密码过长") String password) {
}
