package com.housekeeping.platform.infrastructure.messaging.rocketmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.housekeeping.platform.infrastructure.messaging.EventOutbox;
import com.housekeeping.platform.infrastructure.messaging.OutboxEventWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技术冒烟事件服务：写入一条 {@code SCAFFOLD_PING} 事件。
 *
 * <p>刻意不直接调用 RocketMQ：接口只负责"落库"，投递交给 Relay。
 * 这样才能验证真实生产路径（事务内写 outbox → 异步投递），而不是伪造一条直达 MQ 的捷径。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
public class SmokeEventService {

    private final OutboxEventWriter outboxEventWriter;

    private final RocketMqProperties rocketMqProperties;

    public SmokeEventService(OutboxEventWriter outboxEventWriter, RocketMqProperties rocketMqProperties) {
        this.outboxEventWriter = outboxEventWriter;
        this.rocketMqProperties = rocketMqProperties;
    }

    /**
     * 写入一条冒烟事件。
     *
     * @return 已写入的发件箱记录
     * @throws JsonProcessingException 载荷序列化失败
     */
    public EventOutbox publishScaffoldPing() throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("message", "scaffold ping");
        payload.put("source", "system-smoke-api");
        return outboxEventWriter.append(
                TechEventTypes.SCAFFOLD_PING,
                TechEventTypes.AGGREGATE_SYSTEM,
                "scaffold",
                rocketMqProperties.getTopics().getTechEvent(),
                TechEventTypes.TAG_SCAFFOLD,
                TechEventTypes.AGGREGATE_SYSTEM,
                payload);
    }
}
