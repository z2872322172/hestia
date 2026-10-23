package com.housekeeping.platform.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 发件箱写入服务。
 *
 * <p>调用方式必须是：<b>在业务事务内</b>调用 {@link #append}，让事件与业务变更一起提交。
 * 若业务方法本身没有事务，请把 append 调用放进业务应用服务的事务里，
 * 不要为了"发事件"单独开事务——那会退化成"先提交业务再发事件"的双写问题。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventWriter {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxEventWriter.class);

    private final EventOutboxMapper eventOutboxMapper;

    private final ObjectMapper objectMapper;

    public OutboxEventWriter(EventOutboxMapper eventOutboxMapper, ObjectMapper objectMapper) {
        this.eventOutboxMapper = eventOutboxMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 写入一条待投递事件。
     *
     * @param eventType     事件类型
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合业务 ID
     * @param topic         目标 Topic
     * @param tag           消息 Tag
     * @param messageKey    消息 Key，建议取聚合 ID
     * @param payload       事件载荷对象，会被序列化为 JSON
     * @return 已写入的事件，包含生成的 eventId
     * @throws JsonProcessingException 载荷无法序列化，属于编码错误，必须让事务回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public EventOutbox append(String eventType, String aggregateType, String aggregateId,
                              String topic, String tag, String messageKey, Object payload)
            throws JsonProcessingException {
        EventOutbox event = new EventOutbox();
        event.setEventId(IdGenerator.nextId());
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setTraceId(RequestContext.getTraceId());
        event.setTopic(topic);
        event.setTag(tag);
        event.setMessageKey(messageKey);
        event.setPayloadJson(objectMapper.writeValueAsString(payload));
        event.setStatus(EventOutboxStatus.PENDING.getValue());
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());
        eventOutboxMapper.insert(event);
        LOG.info("写入待投递事件 eventId={} eventType={} topic={} traceId={}",
                event.getEventId(), eventType, topic, event.getTraceId());
        return event;
    }
}
