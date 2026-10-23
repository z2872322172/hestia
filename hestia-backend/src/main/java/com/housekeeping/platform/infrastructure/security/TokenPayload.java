package com.housekeeping.platform.infrastructure.security;

import java.time.Instant;

/**
 * 解析后的令牌载荷。
 *
 * <p>与 docs/modules/01 第 7.2 节的 JWT 字段一一对应：
 * {@code sub / role / tv / jti / iat / exp}。除这六个字段外的内容一律不信任。</p>
 *
 * @param accountId    账号 ID（sub）
 * @param roleCode     活动角色（role）
 * @param tokenVersion 令牌版本（tv）
 * @param tokenId      令牌唯一标识（jti）
 * @param issuedAt     签发时间（iat）
 * @param expiresAt    过期时间（exp）
 */
public record TokenPayload(long accountId, String roleCode, long tokenVersion,
                           String tokenId, Instant issuedAt, Instant expiresAt) {
}
