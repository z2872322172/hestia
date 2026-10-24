package com.housekeeping.platform.notification.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.infrastructure.messaging.ConsumerIdempotencyService;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.RocketMqProperties;
import com.housekeeping.platform.notification.application.NotificationTaskService;
import com.housekeeping.platform.notification.domain.NotificationEventTypes;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 订单事件消费者（消费者组 {@code GID_NOTIFICATION}）：领域事件 → 通知任务。
 *
 * <p>订阅 {@code HK_BOOKING_EVENT} 全部 Tag，只对
 * {@code NotificationEventTypes} 登记的事件类型建任务，
 * 未登记类型（未来订单模块新增事件）直接确认跳过，不堆积死信。</p>
 *
 * <p>幂等顺序与 TechEventConsumer 一致：先写消费幂等记录（重复消息直接确认），
 * 再建任务；建任务内部的 dedupe_key 唯一索引是第二道防线，
 * 两层分别对应"MQ 至少一次投递"与"同事件多消费者"两种重复来源。</p>
 *
 * <p>启动策略：Broker 未就绪时按固定间隔重试，不在启动阶段抛异常。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnExpression("${app.feature.persistence-enabled:true} and ${app.rocketmq.enabled:true}")
public class NotificationEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final RocketMqProperties properties;

    private final ConsumerIdempotencyService idempotencyService;

    private final NotificationTaskService notificationTaskService;

    private final ObjectMapper objectMapper;

    private final ClientServiceProvider provider = ClientServiceProvider.loadService();

    private final Object startLock = new Object();

    private volatile PushConsumer consumer;

    public NotificationEventConsumer(RocketMqProperties properties,
                                     ConsumerIdempotencyService idempotencyService,
                                     NotificationTaskService notificationTaskService,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.idempotencyService = idempotencyService;
        this.notificationTaskService = notificationTaskService;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时确保消费者已启动；失败后等待下一轮重试。
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
            String topic = properties.getTopics().getBookingEvent();
            String consumerGroup = properties.getConsumerGroups().getNotification();
            try {
                ClientConfigurationBuilder configurationBuilder = ClientConfiguration.newBuilder()
                        .setEndpoints(properties.getEndpoints())
                        .setRequestTimeout(properties.getRequestTimeout());
                if (StringUtils.hasText(properties.getNamespace())) {
                    configurationBuilder.setNamespace(properties.getNamespace());
                }
                PushConsumer created = provider.newPushConsumerBuilder()
                        .setClientConfiguration(configurationBuilder.build())
                        .setConsumerGroup(consumerGroup)
                        .setSubscriptionExpressions(Map.of(topic, FilterExpression.SUB_ALL))
                        .setMessageListener(messageView -> handleMessage(
                                messageView.getMessageId().toString(), extractBody(messageView)))
                        .build();
                consumer = created;
                LOG.info("通知事件消费者启动成功 group={} topic={}", consumerGroup, topic);
            } catch (Exception exception) {
                LOG.warn("通知事件消费者启动失败，{} 毫秒后重试：{}",
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
                LOG.info("通知事件消费者已关闭");
            } catch (Exception exception) {
                LOG.warn("关闭通知事件消费者失败", exception);
            }
        }
    }

    private String extractBody(MessageView messageView) {
        ByteBuffer buffer = messageView.getBody();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 消费单条订单事件：登记的事件建通知任务，其余直接确认。
     *
     * <p>建任务抛出的业务异常（几乎只有编码缺陷）按消费失败处理，
     * 让 RocketMQ 重投；重投后由消费幂等记录挡住已成功的部分。</p>
     */
    private ConsumeResult handleMessage(String rocketMqMessageId, String body) {
        String consumerGroup = properties.getConsumerGroups().getNotification();
        try {
            JsonNode envelope = objectMapper.readTree(body);
            String eventId = envelope.path("eventId").asText(rocketMqMessageId);
            String eventType = envelope.path("eventType").asText("UNKNOWN");
            String aggregateId = envelope.path("aggregateId").asText(null);

            Optional<Long> recordId =
                    idempotencyService.begin(consumerGroup, eventId, eventType, aggregateId);
            if (recordId.isEmpty()) {
                return ConsumeResult.SUCCESS;
            }
            try {
                if (NotificationEventTypes.supports(eventType)) {
                    int created = notificationTaskService
                            .createTasksForEvent(eventType, eventId, aggregateId,
                                    extractPayload(envelope))
                            .size();
                    LOG.info("订单事件已生成通知任务 eventId={} eventType={} 新建 {} 条",
                            eventId, eventType, created);
                }
                idempotencyService.markSuccess(recordId.get());
                return ConsumeResult.SUCCESS;
            } catch (RuntimeException exception) {
                idempotencyService.markFailed(recordId.get(), exception.getMessage());
                throw exception;
            }
        } catch (Exception exception) {
            LOG.error("消费订单事件失败 messageId={}", rocketMqMessageId, exception);
            return ConsumeResult.FAILURE;
        }
    }

    /**
     * 提取信封 payload 字段为 Map；缺载荷按空 Map 处理（变量缺失会在渲染阶段失败）。
     */
    private Map<String, Object> extractPayload(JsonNode envelope) {
        JsonNode payload = envelope.path("payload");
        if (!payload.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(payload.size());
        Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            // 标量原样取出；复杂结构保留 JSON 文本，避免渲染层拿到不可读对象
            if (value.isValueNode()) {
                result.put(field.getKey(), value.isNumber()
                        ? value.numberValue() : value.asText());
            } else {
                result.put(field.getKey(), value.toString());
            }
        }
        return result;
    }
}
