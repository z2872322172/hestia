package com.housekeeping.platform.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 序列化与缓存管理器装配。
 *
 * <p>为什么不用默认的 JDK 序列化：JDK 序列化会把类名写进 Redis，
 * 一旦实体改包名或字段类型，历史缓存全部反序列化失败；而且二进制内容无法在
 * Redis 客户端里直接阅读。这里统一使用 JSON + String key。</p>
 *
 * <p>缓存必须设置 TTL：没有过期时间的缓存等于第二份"没有迁移脚本的数据库"。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "redis")
public class RedisConfig {

    /** 缓存默认存活时间；业务模块可通过自定义 RedisCacheConfiguration 覆盖。 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * 通用 RedisTemplate：key 用字符串，value 用 JSON。
     *
     * @param connectionFactory 连接工厂
     * @param objectMapper      应用统一的 Jackson 配置（时间格式、时区一致）
     * @return RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache 使用的缓存管理器。
     *
     * @param connectionFactory 连接工厂
     * @param objectMapper      Jackson 配置
     * @return Redis 缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                // 与 spring.cache.redis.key-prefix 对齐，避免出现两套前缀
                .prefixCacheNameWith(RedisKeyNames.CACHE_PREFIX)
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                // 不缓存 null，避免"缓存穿透保护"变成"缓存污染"
                .disableCachingNullValues();
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(configuration).build();
    }
}
