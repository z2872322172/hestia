package com.housekeeping.platform.infrastructure.messaging.rocketmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.infrastructure.messaging.ConsumerIdempotencyService;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * 技术冒烟事件消费者（消费者组 {@code GID_TECH_SMOKE}）。
 *
 * <p>职责：证明"Outbox → RocketMQ → 幂等消费"整条链路可用，不做任何业务处理。</p>
 *
 * <p>启动策略：Broker 未就绪时不在启动阶段抛异常，而是按
 * {@code app.rocketmq.consumer-start-retry-ms} 定时重试，避免本地开发必须严格按顺序启动容器。</p>
 */
@Component
@ConditionalOnExpression("${app.feature.persistence-enabled:true} and ${app.rocketmq.enabled:true}")
public class TechEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TechEventConsumer.class);

    private final RocketMqProperties properties;

    private final ConsumerIdempotencyService idempotencyService;

    private final ObjectMapper objectMapper;

    private final ClientServiceProvider provider = ClientServiceProvider.loadService();

    private final Object startLock = new Object();

    private volatile PushConsumer consumer;

    public TechEventConsumer(RocketMqProperties properties,
                             ConsumerIdempotencyService idempotencyService,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时确保消费者已启动。首次由 Spring 调度触发，失败后按固定间隔重试。
     */
    @Scheduled(initialDelayString = "${app.rocketmq.consumer-start-retry-ms:30000}",
            fixedDelayString = "${app.rocketmq.consumer-start-retry-ms:30000}")
    public void ensureStarted() {
        if (consumer != null) {
            return;
        }
        synchronized (startLock) {
            if (consumer != null) {
                return;
            }
            String topic = properties.getTopics().getTechEvent();
            String consumerGroup = properties.getConsumerGroups().getTechSmoke();
            try {
                ClientConfigurationBuilder configurationBuilder = ClientConfiguration.newBuilder()
                        .setEndpoints(properties.getEndpoints())
                        .setRequestTimeout(properties.getRequestTimeout());
                if (StringUtils.hasText(properties.getNamespace())) {
                    configurationBuilder.setNamespace(properties.getNamespace());
                }
                // 5.0.x 客户端在 build() 时即完成启动与订阅，没有单独的 start() 方法
                PushConsumer created = provider.newPushConsumerBuilder()
                        .setClientConfiguration(configurationBuilder.build())
                        .setConsumerGroup(consumerGroup)
                        // 订阅技术事件 Topic 的全部 Tag
                        .setSubscriptionExpressions(Map.of(topic, FilterExpression.SUB_ALL))
                        .setMessageListener(messageView -> handleMessage(
                                messageView.getMessageId().toString(),
                                extractBody(messageView)))
                        .build();
                consumer = created;
                LOG.info("技术事件消费者启动成功 group={} topic={} endpoints={}",
                        consumerGroup, topic, properties.getEndpoints());
            } catch (Exception exception) {
                // Broker 尚未就绪属于本地开发的常见情况，用 WARN 提示并等待下一轮重试
                LOG.warn("技术事件消费者启动失败，{} 毫秒后重试：{}",
                        properties.getConsumerStartRetryMs(), exception.getMessage());
            }
        }
    }

    /**
     * 关闭消费者。
     */
    @PreDestroy
    public void close() {
        PushConsumer current = consumer;
        consumer = null;
        if (current != null) {
            try {
                current.close();
                LOG.info("技术事件消费者已关闭");
            } catch (Exception exception) {
                LOG.warn("关闭技术事件消费者失败", exception);
            }
        }
    }

    /**
     * 提取消息体。RocketMQ 5.x 的 {@link MessageView#getBody()} 返回 ByteBuffer，
     * 必须先按 remaining 读取再解码，不能直接 toString。
     *
     * @param messageView 消息视图
     * @return 消息体字符串
     */
    private String extractBody(MessageView messageView) {
        ByteBuffer buffer = messageView.getBody();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 消费单条消息。
     *
     * <p>幂等顺序不可颠倒：先写幂等记录，再执行业务；重复消息直接确认成功，
     * 让 RocketMQ 停止重投。</p>
     *
     * @param rocketMqMessageId RocketMQ 消息 ID，仅用于日志
     * @param body              事件信封 JSON
     * @return 消费结果
     */
    private ConsumeResult handleMessage(String rocketMqMessageId, String body) {
        String consumerGroup = properties.getConsumerGroups().getTechSmoke();
        try {
            JsonNode envelope = objectMapper.readTree(body);
            String eventId = envelope.path("eventId").asText(rocketMqMessageId);
            String eventType = envelope.path("eventType").asText("UNKNOWN");
            String aggregateId = envelope.path("aggregateId").asText(null);

            Optional<Long> recordId = idempotencyService.begin(consumerGroup, eventId, eventType, aggregateId);
            if (recordId.isEmpty()) {
                return ConsumeResult.SUCCESS;
            }
            try {
                // 技术冒烟事件没有业务副作用，记录消费事实即可
                LOG.info("已消费技术事件 eventId={} eventType={} aggregateId={}",
                        eventId, eventType, aggregateId);
                idempotencyService.markSuccess(recordId.get());
                return ConsumeResult.SUCCESS;
            } catch (RuntimeException exception) {
                idempotencyService.markFailed(recordId.get(), exception.getMessage());
                throw exception;
            }
        } catch (Exception exception) {
            // 返回 FAILURE 让 RocketMQ 按退避策略重投，超过次数后进入死信队列
            LOG.error("消费技术事件失败 messageId={}", rocketMqMessageId, exception);
            return ConsumeResult.FAILURE;
        }
    }
}
