package com.housekeeping.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.infrastructure.map.MapProperties;
import com.housekeeping.platform.infrastructure.map.MapProvider;
import com.housekeeping.platform.infrastructure.map.MapProviderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地图 Provider 选择测试。
 *
 * <p>验证配置切换生效、默认使用 Mock、以及"配置了 amap 但没给 Key"时启动阶段就失败，
 * 而不是等到第一次地址解析才暴露问题。</p>
 */
class MapProviderSelectionTest {

    /** 正常场景：提供带 Key 的配置。 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(MapProviderConfig.class, KeyedMapPropertiesConfig.class);

    @Test
    @DisplayName("默认使用 Mock Provider，无外网也能工作")
    void defaultsToMockProvider() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MapProvider.class);
            assertThat(context.getBean(MapProvider.class).providerName()).isEqualTo("mock");
        });
    }

    @Test
    @DisplayName("配置 app.map.provider=amap 时切换到高德实现")
    void switchesToAmapProvider() {
        runner.withPropertyValues("app.map.provider=amap")
                .run(context -> {
                    assertThat(context).hasSingleBean(MapProvider.class);
                    assertThat(context.getBean(MapProvider.class).providerName()).isEqualTo("amap");
                });
    }

    @Test
    @DisplayName("amap 未配置 Key 时启动失败并给出明确原因")
    void amapWithoutKeyFailsFast() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(MapProviderConfig.class, KeylessMapPropertiesConfig.class)
                .withPropertyValues("app.map.provider=amap")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("AMAP_WEB_SERVICE_KEY");
                });
    }

    /**
     * 带 Key 的地图配置。
     */
    @Configuration
    static class KeyedMapPropertiesConfig {

        @Bean
        MapProperties mapProperties() {
            MapProperties properties = new MapProperties();
            properties.getAmap().setWebServiceKey("test-key");
            return properties;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /**
     * 不带 Key 的地图配置，用于验证启动期快速失败。
     */
    @Configuration
    static class KeylessMapPropertiesConfig {

        @Bean
        MapProperties mapProperties() {
            MapProperties properties = new MapProperties();
            properties.setProvider("amap");
            // 故意不设置 webServiceKey
            return properties;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
