package com.housekeeping.platform.infrastructure.cache;

/**
 * Redis Key 命名规范。
 *
 * <p>所有 key 统一以 {@code app:} 开头（docs/05 第 9.2 节），目的有三个：</p>
 * <ol>
 *   <li>多应用共用一套 Redis 时便于隔离与批量清理；</li>
 *   <li>按业务域分组后，运维可以直接看出哪类数据占用了内存；</li>
 *   <li>Key 由常量方法生成，禁止在业务代码里手写字符串拼接，避免同一语义出现多种写法。</li>
 * </ol>
 *
 * <p>任何 key 都必须设置 TTL 或有明确的清理策略，禁止写入无过期时间的临时数据。</p>
 */
public final class RedisKeyNames {

    public static final String PREFIX = "app:";

    /** 缓存前缀，与 spring.cache.redis.key-prefix 保持一致。 */
    public static final String CACHE_PREFIX = PREFIX + "cache:";

    /** 认证相关：刷新令牌、令牌撤销列表、登录限流。 */
    public static final String AUTH_PREFIX = PREFIX + "auth:";

    /** 接口限流计数器。 */
    public static final String RATE_LIMIT_PREFIX = PREFIX + "rate:";

    /** 服务人员在线状态（临时数据，不是业务事实来源）。 */
    public static final String PRESENCE_PREFIX = PREFIX + "presence:";

    /** 服务人员实时位置 GEO 集合，只保留最近几分钟的位置。 */
    public static final String GEO_PREFIX = PREFIX + "geo:";

    /** 分布式锁。 */
    public static final String LOCK_PREFIX = PREFIX + "lock:";

    /** WebSocket 会话映射。 */
    public static final String WS_PREFIX = PREFIX + "ws:";

    private RedisKeyNames() {
    }

    /**
     * 刷新令牌 key。
     *
     * @param jti 令牌唯一标识
     * @return 完整 key
     */
    public static String refreshToken(String jti) {
        return AUTH_PREFIX + "refresh:" + jti;
    }

    /**
     * 已撤销的访问令牌 key（退出登录后写入，TTL 取令牌剩余有效期）。
     *
     * @param jti 令牌唯一标识
     * @return 完整 key
     */
    public static String revokedAccessToken(String jti) {
        return AUTH_PREFIX + "revoked:" + jti;
    }

    /**
     * 登录限流计数器 key。
     *
     * @param dimension 限流维度，如 {@code ip} 或 {@code account}
     * @param value     维度取值，如 IP 地址或账号 ID
     * @return 完整 key
     */
    public static String loginRateLimit(String dimension, String value) {
        return RATE_LIMIT_PREFIX + "login:" + dimension + ":" + value;
    }

    /**
     * 服务人员在线状态 key。
     *
     * @param workerId 人员 ID
     * @return 完整 key
     */
    public static String workerPresence(Long workerId) {
        return PRESENCE_PREFIX + "worker:" + workerId;
    }

    /**
     * 分布式锁 key。
     *
     * @param business 业务标识，如 {@code booking:slot:1001}
     * @return 完整 key
     */
    public static String lock(String business) {
        return LOCK_PREFIX + business;
    }
}
