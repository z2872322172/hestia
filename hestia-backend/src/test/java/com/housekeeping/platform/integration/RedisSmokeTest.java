package com.housekeeping.platform.integration;

import com.housekeeping.platform.infrastructure.cache.RedisKeyNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 冒烟测试：验证连接、读写与 key 命名规范。
 *
 * <p>同时断言 TTL 生效——没有过期时间的缓存会在生产里慢慢变成"第二数据库"。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class RedisSmokeTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine"))
            .withExposedPorts(6379);

    @Test
    @DisplayName("Redis 可连接，key 使用 app: 前缀且可设置 TTL")
    void readsAndWritesWithAppKeyPrefix() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            String key = RedisKeyNames.workerPresence(1001L);

            template.opsForValue().set(key, "ONLINE", Duration.ofSeconds(30));

            assertThat(key).startsWith(RedisKeyNames.PREFIX);
            assertThat(template.opsForValue().get(key)).isEqualTo("ONLINE");
            assertThat(template.getExpire(key)).isGreaterThan(0L);
        } finally {
            connectionFactory.destroy();
        }
    }
}
