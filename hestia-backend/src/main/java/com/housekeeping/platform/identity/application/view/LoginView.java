package com.housekeeping.platform.identity.application.view;

/**
 * 登录结果（docs/modules/01 第 14.4 节）。
 *
 * @param accessToken 访问令牌
 * @param expiresIn   有效期（秒）
 * @param account     账号信息
 */
public record LoginView(String accessToken, long expiresIn, AccountView account) {
}
