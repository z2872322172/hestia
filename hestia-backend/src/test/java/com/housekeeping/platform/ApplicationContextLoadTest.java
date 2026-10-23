package com.housekeeping.platform;

import com.housekeeping.platform.infrastructure.map.MapProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文加载测试。
 *
 * <p>test profile 关闭了数据源、Redis、RocketMQ、MinIO，因此本测试验证的是
 * "在没有外部基础设施的机器上，应用依然能完整装配并启动"。
 * 这也是 CI 与本地默认 {@code mvn verify} 的前提。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MapProvider mapProvider;

    @Test
    @DisplayName("应用上下文可以加载，且默认使用 Mock 地图 Provider")
    void contextLoadsWithMockMapProvider() {
        assertThat(applicationContext.getBean(HousekeepingPlatformApplication.class)).isNotNull();
        assertThat(mapProvider.providerName()).isEqualTo("mock");
    }

    @Test
    @DisplayName("test profile 下不装配依赖数据源的 Bean")
    void persistenceBeansAreAbsentInTestProfile() {
        assertThat(applicationContext.getBeanNamesForType(
                com.housekeeping.platform.infrastructure.messaging.EventOutboxMapper.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(
                com.housekeeping.platform.infrastructure.storage.minio.FileObjectMapper.class)).isEmpty();
    }
}
