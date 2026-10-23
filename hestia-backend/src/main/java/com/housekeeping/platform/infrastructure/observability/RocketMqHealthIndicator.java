package com.housekeeping.platform.infrastructure.observability;

import com.housekeeping.platform.infrastructure.messaging.rocketmq.RocketMqProducerHolder;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.RocketMqProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 健康检查，注册为 {@code /actuator/health} 中的 {@code rocketMq} 组件。
 *
 * <p>判断依据是 Producer 是否已成功建立连接。刻意不在健康检查里主动建连：
 * 健康检查被频繁轮询，反复建连会放大 Broker 压力，也让"首次发送才初始化"的设计失效。</p>
 */
@Component("rocketMqHealthIndicator")
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqHealthIndicator implements HealthIndicator {

    private final RocketMqProducerHolder producerHolder;

    private final RocketMqProperties properties;

    public RocketMqHealthIndicator(RocketMqProducerHolder producerHolder, RocketMqProperties properties) {
        this.producerHolder = producerHolder;
        this.properties = properties;
    }

    @Override
    public Health health() {
        boolean ready = producerHolder.isReady();
        Health.Builder builder = ready ? Health.up() : Health.down();
        return builder
                .withDetail("endpoints", properties.getEndpoints())
                .withDetail("producer", ready ? "READY" : "NOT_INITIALIZED")
                .build();
    }
}
