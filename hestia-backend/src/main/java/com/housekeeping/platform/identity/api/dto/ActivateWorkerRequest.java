package com.housekeeping.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 服务人员激活请求。
 *
 * @param token    一次性激活凭证
 * @param password 新密码
 */
public record ActivateWorkerRequest(
        @NotBlank(message = "激活凭证不能为空") @Size(max = 128, message = "激活凭证过长") String token,
        @NotBlank(message = "密码不能为空") @Size(max = 64, message = "密码过长") String password) {
}
