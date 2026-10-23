package com.housekeeping.platform.infrastructure.messaging.rocketmq;

import com.housekeeping.platform.infrastructure.messaging.EventEnvelopeAssembler;
import com.housekeeping.platform.infrastructure.messaging.EventOutbox;
import com.housekeeping.platform.infrastructure.messaging.EventPublishException;
import com.housekeeping.platform.infrastructure.messaging.EventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 RocketMQ 的事件投递实现。
 *
 * <p>只负责"装配信封 + 交给客户端"，重试、状态流转由 Relay 处理，
 * 职责分离后 Relay 的逻辑可以脱离 Broker 单测。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqEventPublisher implements EventPublisher {

    private final RocketMqProducerHolder producerHolder;

    private final EventEnvelopeAssembler envelopeAssembler;

    public RocketMqEventPublisher(RocketMqProducerHolder producerHolder,
                                  EventEnvelopeAssembler envelopeAssembler) {
        this.producerHolder = producerHolder;
        this.envelopeAssembler = envelopeAssembler;
    }

    @Override
    public void publish(EventOutbox event) {
        String body;
        try {
            body = envelopeAssembler.toJson(event);
        } catch (Exception exception) {
            // 信封序列化失败是编码缺陷，重试没有意义，直接转 DEAD 更合适
            throw new EventPublishException("事件信封序列化失败，eventId=" + event.getEventId(), exception);
        }
        producerHolder.send(event, body);
    }
}
