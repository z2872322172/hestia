package com.housekeeping.platform.infrastructure.messaging.rocketmq;

import com.housekeeping.platform.infrastructure.messaging.EventOutbox;
import com.housekeeping.platform.infrastructure.messaging.EventPublishException;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ Producer 持有者（懒加载 + 可重试初始化）。
 *
 * <p>为什么不在启动时强制创建 Producer：本地开发经常先启动应用再启动 Broker，
 * 如果 Producer 在 {@code @Bean} 阶段连接失败，整个应用起不来，排查成本很高。
 * 这里改成"首次发送时初始化"，失败只让当条事件进入退避重试，应用本身照常可用。</p>
 *
 * <p>Producer 是长连接客户端，必须单例复用；每次发送新建会耗尽连接并显著增加延迟。</p>
 *
 * <p>注意：RocketMQ 5.x 客户端在 {@code build()} 时即完成启动与连接，没有单独的 {@code start()} 方法。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqProducerHolder {

    private static final Logger LOG = LoggerFactory.getLogger(RocketMqProducerHolder.class);

    private final RocketMqProperties properties;

    private final ClientServiceProvider provider = ClientServiceProvider.loadService();

    private final Object initializationLock = new Object();

    private volatile Producer producer;

    public RocketMqProducerHolder(RocketMqProperties properties) {
        this.properties = properties;
    }

    /**
     * 发送一条事件消息。
     *
     * @param event 发件箱事件，提供 topic / tag / key
     * @param body  事件信封 JSON
     * @throws EventPublishException 客户端未就绪或发送失败
     */
    public void send(EventOutbox event, String body) {
        try {
            MessageBuilder builder = provider.newMessageBuilder()
                    .setTopic(event.getTopic())
                    .setBody(body.getBytes(StandardCharsets.UTF_8));
            if (StringUtils.hasText(event.getTag())) {
                builder.setTag(event.getTag());
            }
            // 用聚合 ID 作为消息 Key，保证同一聚合的事件落在同一队列，消费端顺序可预期
            String messageKey = StringUtils.hasText(event.getMessageKey())
                    ? event.getMessageKey() : event.getEventId();
            builder.setKeys(messageKey);

            producer().send(builder.build());
        } catch (EventPublishException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EventPublishException("RocketMQ 发送失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 判断 Producer 是否已就绪，供健康检查使用。
     *
     * @return 已创建并启动返回 {@code true}
     */
    public boolean isReady() {
        return producer != null;
    }

    /**
     * 关闭 Producer，释放 gRPC 连接。
     */
    @PreDestroy
    public void close() {
        Producer current = producer;
        producer = null;
        if (current != null) {
            try {
                current.close();
                LOG.info("RocketMQ Producer 已关闭");
            } catch (Exception exception) {
                LOG.warn("关闭 RocketMQ Producer 失败", exception);
            }
        }
    }

    private Producer producer() {
        Producer current = producer;
        if (current != null) {
            return current;
        }
        synchronized (initializationLock) {
            if (producer != null) {
                return producer;
            }
            try {
                ClientConfiguration clientConfiguration = buildClientConfiguration();
                Producer created = provider.newProducerBuilder()
                        .setClientConfiguration(clientConfiguration)
                        .build();
                producer = created;
                LOG.info("RocketMQ Producer 初始化成功 endpoints={}", properties.getEndpoints());
                return created;
            } catch (Exception exception) {
                // 不缓存失败结果：Broker 恢复后下一次发送应能自动重连
                throw new EventPublishException("RocketMQ Producer 初始化失败：" + exception.getMessage(), exception);
            }
        }
    }

    private ClientConfiguration buildClientConfiguration() {
        ClientConfigurationBuilder builder = ClientConfiguration.newBuilder()
                .setEndpoints(properties.getEndpoints())
                .setRequestTimeout(properties.getRequestTimeout());
        if (StringUtils.hasText(properties.getNamespace())) {
            builder.setNamespace(properties.getNamespace());
        }
        return builder.build();
    }
}
