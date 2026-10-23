package com.housekeeping.platform.infrastructure.security;

/**
 * 新签发的访问令牌。
 *
 * @param token            JWT 字符串
 * @param expiresInSeconds 有效期秒数，供前端安排刷新时机
 * @param tokenId          令牌唯一标识（jti），退出登录时写入撤销列表
 */
public record IssuedToken(String token, long expiresInSeconds, String tokenId) {
}
