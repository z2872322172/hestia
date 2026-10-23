package com.housekeeping.platform.infrastructure.observability;

import com.housekeeping.platform.infrastructure.map.MapProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 地图服务健康检查，注册为 {@code /actuator/health} 中的 {@code map} 组件。
 *
 * <p>Mock 实现恒为 UP；高德实现会做一次带超时的真实探测，
 * 外部服务异常时暴露为 DOWN，而不是静默吞掉（docs/05 第 16 节）。</p>
 */
@Component("mapHealthIndicator")
public class MapHealthIndicator implements HealthIndicator {

    private final MapProvider mapProvider;

    public MapHealthIndicator(MapProvider mapProvider) {
        this.mapProvider = mapProvider;
    }

    @Override
    public Health health() {
        boolean healthy = mapProvider.healthy();
        Health.Builder builder = healthy ? Health.up() : Health.down();
        return builder.withDetail("provider", mapProvider.providerName()).build();
    }
}
