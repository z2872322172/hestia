package com.housekeeping.platform.common.security;

import java.time.Instant;

/**
 * 已认证账号（当前请求的身份快照）。
 *
 * <p>只放鉴权必需的最小字段：账号 ID、对外编号、活动角色、令牌版本、令牌 ID、过期时间。
 * 手机号、真实姓名、地址等个人信息一律不进安全上下文，避免被日志或异常处理器顺带打印。</p>
 *
 * <p>放在 {@code common} 而不是 identity：它是"任何模块读当前身份"的共享形状，
 * 业务模块读取时不应反向依赖身份模块的实现类。</p>
 *
 * @param accountId    账号 ID
 * @param accountNo    对外账号编号
 * @param roleCode     本次请求的活动角色
 * @param tokenVersion 令牌版本，与账号当前版本一致才有效
 * @param tokenId      令牌唯一标识（jti），用于单设备退出
 * @param expiresAt    令牌过期时间，退出时用于计算撤销条目的 TTL
 */
public record AuthenticatedAccount(Long accountId, String accountNo, String roleCode,
                                   long tokenVersion, String tokenId, Instant expiresAt) {
}
