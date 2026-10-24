package com.housekeeping.platform.infrastructure.messaging.rocketmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RocketMQ 5.x 客户端配置。
 *
 * <p>端点指向 RocketMQ Proxy 的 gRPC 端口（默认 8081），不是 NameServer 的 9876：
 * 5.x 客户端走 gRPC 协议，只有 Proxy 提供该入口。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.rocketmq")
public class RocketMqProperties {

    /** 是否启用 RocketMQ。关闭后不创建 Producer/Consumer，Relay 也不启动。 */
    private boolean enabled = true;

    /** gRPC 端点，形如 {@code localhost:8081}。 */
    private String endpoints = "localhost:8081";

    /** 命名空间；多环境共用一个集群时用于隔离 Topic。 */
    private String namespace = "";

    /** 请求超时，避免 Broker 无响应时请求线程被长期占用。 */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /** Consumer 启动失败后的重试间隔（毫秒）。 */
    private long consumerStartRetryMs = 30_000L;

    private Topics topics = new Topics();

    private ConsumerGroups consumerGroups = new ConsumerGroups();

    /**
     * Topic 配置。
     *
     * <p>技术阶段只有 {@code HK_TECH_EVENT}；业务 Topic 随对应批次加入
     * （订单事件在批次 3 接入）。</p>
     */
    @Data
    public static class Topics {

        /** 技术冒烟事件 Topic。 */
        private String techEvent = "HK_TECH_EVENT";

        /** 订单领域事件 Topic（订单状态机关键转移点，Outbox 投递）。 */
        private String bookingEvent = "HK_BOOKING_EVENT";
    }

    /**
     * 消费者组配置。
     */
    @Data
    public static class ConsumerGroups {

        /** 技术冒烟消费者组。 */
        private String techSmoke = "GID_TECH_SMOKE";

        /** 通知模块消费者组（订单事件 → 通知任务）。 */
        private String notification = "GID_NOTIFICATION";
    }
}
