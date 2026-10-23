package com.housekeeping.platform.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * 事件信封装配器。
 *
 * <p>数据库里事件字段是"列"，消息里需要的是"信封 + 载荷"的整段 JSON。
 * 装配逻辑集中在这里，保证 Relay 与消费端对信封结构只有一种解释。</p>
 */
@Component
public class EventEnvelopeAssembler {

    private final ObjectMapper objectMapper;

    public EventEnvelopeAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把发件箱行装配成标准信封 JSON。
     *
     * @param event 发件箱事件
     * @return 信封 JSON
     * @throws JsonProcessingException 载荷不是合法 JSON（理论上不会发生，写入时已校验）
     */
    public String toJson(EventOutbox event) throws JsonProcessingException {
        DomainEvent<Object> envelope = new DomainEvent<>(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getCreatedAt() == null
                        ? java.time.OffsetDateTime.now()
                        : event.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                event.getTraceId(),
                DomainEvent.CURRENT_VERSION,
                objectMapper.readTree(event.getPayloadJson()));
        return objectMapper.writeValueAsString(envelope);
    }
}
