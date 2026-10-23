package com.housekeeping.platform.infrastructure.messaging;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 消费幂等服务。
 *
 * <p>标准用法：</p>
 * <pre>
 * Optional&lt;Long&gt; recordId = idempotency.begin(group, eventId, eventType, aggregateId);
 * if (recordId.isEmpty()) { return SUCCESS; }   // 重复消息，直接确认
 * try { 业务处理; idempotency.markSuccess(recordId.get()); }
 * catch (Exception e) { idempotency.markFailed(recordId.get(), e); throw e; }
 * </pre>
 *
 * <p>为什么"先插入再处理"而不是"先查再插"：并发消费同一条消息时，
 * 两个线程都会查到"不存在"；只有唯一索引的插入冲突能可靠地区分出唯一的处理者。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
public class ConsumerIdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerIdempotencyService.class);

    private static final int MAX_ERROR_LENGTH = 1000;

    private final ConsumerMessageMapper consumerMessageMapper;

    public ConsumerIdempotencyService(ConsumerMessageMapper consumerMessageMapper) {
        this.consumerMessageMapper = consumerMessageMapper;
    }

    /**
     * 认领一条消息。
     *
     * @param consumerGroup 消费者组
     * @param messageId     消息 ID（使用事件 eventId）
     * @param eventType     事件类型
     * @param aggregateId   聚合业务 ID
     * @return 认领成功返回记录 ID；消息已被处理过返回空
     */
    public Optional<Long> begin(String consumerGroup, String messageId, String eventType, String aggregateId) {
        ConsumerMessage record = new ConsumerMessage();
        record.setConsumerGroup(consumerGroup);
        record.setMessageId(messageId);
        record.setEventType(eventType);
        record.setAggregateId(aggregateId);
        record.setStatus(ConsumerMessageStatus.PROCESSING.getValue());
        record.setConsumedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        try {
            consumerMessageMapper.insert(record);
            return Optional.of(record.getId());
        } catch (DataIntegrityViolationException exception) {
            // 唯一键冲突即"重复消息"，属于正常幂等分支，用 DEBUG 记录避免刷日志
            LOG.debug("消息已消费过，跳过 consumerGroup={} messageId={}", consumerGroup, messageId);
            return Optional.empty();
        }
    }

    /**
     * 标记消费成功。
     *
     * @param recordId 幂等记录 ID
     */
    public void markSuccess(Long recordId) {
        updateStatus(recordId, ConsumerMessageStatus.SUCCESS, null);
    }

    /**
     * 标记消费失败；调用方通常还需要抛出异常触发 RocketMQ 重试。
     *
     * @param recordId 幂等记录 ID
     * @param error    失败原因，会被截断
     */
    public void markFailed(Long recordId, String error) {
        updateStatus(recordId, ConsumerMessageStatus.FAILED, truncate(error));
    }

    /**
     * 查询某条消息的消费记录，用于 smoke 验证与运营排查。
     *
     * @param consumerGroup 消费者组
     * @param messageId     消息 ID
     * @return 消费记录；不存在时为空
     */
    public Optional<ConsumerMessage> find(String consumerGroup, String messageId) {
        return Optional.ofNullable(consumerMessageMapper.selectByGroupAndMessageId(consumerGroup, messageId));
    }

    private void updateStatus(Long recordId, ConsumerMessageStatus status, String error) {
        LambdaUpdateWrapper<ConsumerMessage> wrapper = new LambdaUpdateWrapper<ConsumerMessage>()
                .eq(ConsumerMessage::getId, recordId)
                .set(ConsumerMessage::getStatus, status.getValue())
                .set(ConsumerMessage::getUpdatedAt, LocalDateTime.now())
                .set(ConsumerMessage::getErrorMessage, error);
        consumerMessageMapper.update(null, wrapper);
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
