package com.housekeeping.platform.infrastructure.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 地图 Provider 装配：按 {@code app.map.provider} 二选一。
 *
 * <p>用 {@code @ConditionalOnProperty} 而不是运行期 if-else：
 * 配置错了应该在启动阶段就暴露，而不是等到第一次调用地址解析才报错。</p>
 */
@Configuration
public class MapProviderConfig {

    /**
     * Mock 实现（默认）。
     *
     * @return Mock 地图 Provider
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.map", name = "provider", havingValue = "mock", matchIfMissing = true)
    public MapProvider mockMapProvider() {
        return new MockMapProvider();
    }

    /**
     * 高德实现，仅在显式配置 {@code app.map.provider=amap} 时启用。
     *
     * @param mapProperties 地图配置
     * @param objectMapper  Jackson 实例
     * @return 高德地图 Provider
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.map", name = "provider", havingValue = "amap")
    public MapProvider amapMapProvider(MapProperties mapProperties, ObjectMapper objectMapper) {
        return new AmapMapProvider(mapProperties.getAmap(), objectMapper);
    }
}
