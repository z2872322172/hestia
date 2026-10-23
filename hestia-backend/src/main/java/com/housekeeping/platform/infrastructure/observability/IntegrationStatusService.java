package com.housekeeping.platform.infrastructure.observability;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 集成状态查询服务。
 *
 * <p>直接复用 Actuator 的健康聚合结果，而不是另写一套探测逻辑：
 * 两套逻辑迟早会不一致，运维看到"接口说正常、健康检查说 DOWN"时无从判断。</p>
 */
@Service
public class IntegrationStatusService {

    private final HealthEndpoint healthEndpoint;

    public IntegrationStatusService(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    /**
     * 获取各集成组件的健康状态。
     *
     * @return 组件名到状态码的有序映射，如 {@code db=UP, redis=UP, minio=DOWN}
     */
    public Map<String, String> componentStatuses() {
        HealthComponent health = healthEndpoint.health();
        Map<String, String> statuses = new LinkedHashMap<>();
        if (health instanceof CompositeHealth compositeHealth) {
            compositeHealth.getComponents().forEach((name, component) ->
                    statuses.put(name, component.getStatus().getCode()));
        } else {
            statuses.put("application", health.getStatus().getCode());
        }
        return statuses;
    }
}
