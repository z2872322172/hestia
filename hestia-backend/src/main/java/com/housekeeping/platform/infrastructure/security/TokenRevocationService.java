package com.housekeeping.platform.infrastructure.security;

import com.housekeeping.platform.infrastructure.cache.RedisKeyNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 单设备令牌撤销列表（Redis）。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>只撤销"当前这个 jti"，TTL 取令牌剩余有效期——令牌自然过期后条目自动消失，
 *       不需要额外清理任务；</li>
 *   <li>Redis 不可用时<b>不阻断退出登录</b>：写失败只记 WARN，该令牌等待自然过期。
 *       账号级失效（停用、改密、角色暂停）走 {@code token_version}，不依赖 Redis，
 *       所以"Redis 挂了导致无法强制下线"这一最坏情况不会发生；</li>
 *   <li>日志只记录 jti，不记录令牌本身。</li>
 * </ul>
 */
@Component
public class TokenRevocationService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenRevocationService.class);

    private static final String REVOKED_FLAG = "1";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public TokenRevocationService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 撤销单个令牌。
     *
     * @param tokenId   令牌唯一标识（jti）
     * @param expiresAt 令牌过期时间，用于计算 TTL
     */
    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null) {
            return;
        }
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            // 已经过期，写进去也没有意义
            return;
        }
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            LOG.warn("未装配 Redis，跳过令牌撤销，令牌将等待自然过期 jti={}", tokenId);
            return;
        }
        try {
            template.opsForValue().set(RedisKeyNames.revokedAccessToken(tokenId),
                    REVOKED_FLAG, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException exception) {
            LOG.warn("写入令牌撤销列表失败，令牌将等待自然过期 jti={}", tokenId, exception);
        }
    }

    /**
     * 判断令牌是否已被撤销。
     *
     * <p>Redis 不可用时返回 {@code false}（按"未撤销"放行）：
     * 可用性优先，账号级失效仍由 {@code token_version} 兜底。</p>
     *
     * @param tokenId 令牌唯一标识
     * @return 是否已撤销
     */
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(template.hasKey(RedisKeyNames.revokedAccessToken(tokenId)));
        } catch (RuntimeException exception) {
            LOG.warn("读取令牌撤销列表失败，按未撤销处理 jti={}", tokenId, exception);
            return false;
        }
    }
}
