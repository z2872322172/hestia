package com.housekeeping.platform.infrastructure.messaging;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Relay：把发件箱里的待投递事件发送到消息中间件。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>领取与发送分离：领取是短事务，发送在事务外，避免长时间持锁；</li>
 *   <li>失败不抛异常：单条事件失败只影响它自己，其余事件照常投递；</li>
 *   <li>指数退避 + 次数上限：Broker 长时间不可用时不打满日志，也不无限重试；</li>
 *   <li>超过上限置为 DEAD，等人工介入，禁止静默丢弃（资金类事件尤其如此）。</li>
 * </ul>
 */
@Service
@ConditionalOnExpression("${app.feature.persistence-enabled:true} and ${app.rocketmq.enabled:true}")
public class OutboxRelayService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelayService.class);

    /** 退避上限：10 分钟，避免 Broker 恢复后仍要等很久。 */
    private static final long MAX_BACKOFF_SECONDS = 600L;

    /** 存入 last_error 的最大长度，与表字段一致。 */
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxClaimService outboxClaimService;

    private final EventOutboxMapper eventOutboxMapper;

    private final EventPublisher eventPublisher;

    private final OutboxRelayProperties properties;

    public OutboxRelayService(OutboxClaimService outboxClaimService,
                              EventOutboxMapper eventOutboxMapper,
                              EventPublisher eventPublisher,
                              OutboxRelayProperties properties) {
        this.outboxClaimService = outboxClaimService;
        this.eventOutboxMapper = eventOutboxMapper;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * 执行一轮投递。
     *
     * @return 本轮成功投递的事件数
     */
    public int relayOnce() {
        List<EventOutbox> batch = outboxClaimService.claim(properties.getBatchSize());
        if (batch.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (EventOutbox event : batch) {
            try {
                eventPublisher.publish(event);
                markSent(event);
                successCount++;
            } catch (RuntimeException exception) {
                // 单条失败不影响同批其他事件；失败原因截断后落库，便于运营排查
                markRetry(event, exception);
            }
        }
        LOG.debug("Outbox Relay 完成一轮：领取 {} 条，成功 {} 条", batch.size(), successCount);
        return successCount;
    }

    private void markSent(EventOutbox event) {
        LambdaUpdateWrapper<EventOutbox> wrapper = new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, event.getId())
                .eq(EventOutbox::getStatus, EventOutboxStatus.SENDING.getValue())
                .set(EventOutbox::getStatus, EventOutboxStatus.SENT.getValue())
                .set(EventOutbox::getSentAt, LocalDateTime.now())
                .set(EventOutbox::getLastError, null);
        eventOutboxMapper.update(null, wrapper);
    }

    private void markRetry(EventOutbox event, RuntimeException exception) {
        int retryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        String error = truncate(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        boolean exhausted = retryCount >= properties.getMaxRetry();

        LambdaUpdateWrapper<EventOutbox> wrapper = new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, event.getId())
                .set(EventOutbox::getRetryCount, retryCount)
                .set(EventOutbox::getLastError, error)
                .set(EventOutbox::getStatus, exhausted
                        ? EventOutboxStatus.DEAD.getValue()
                        : EventOutboxStatus.PENDING.getValue())
                .set(EventOutbox::getNextRetryAt, exhausted ? null : LocalDateTime.now().plusSeconds(backoffSeconds(retryCount)));
        eventOutboxMapper.update(null, wrapper);

        if (exhausted) {
            LOG.error("事件投递超过最大重试次数，转人工处理 eventId={} eventType={} retry={} error={}",
                    event.getEventId(), event.getEventType(), retryCount, error);
        } else {
            LOG.warn("事件投递失败，等待退避重试 eventId={} retry={} error={}",
                    event.getEventId(), retryCount, error);
        }
    }

    /**
     * 计算第 n 次失败后的退避秒数：base * 2^(n-1)，上限 10 分钟。
     *
     * @param retryCount 已失败次数
     * @return 退避秒数
     */
    private long backoffSeconds(int retryCount) {
        long base = Math.max(1L, properties.getRetryBackoffSeconds());
        int shift = Math.min(retryCount - 1, 20);
        long seconds = base << shift;
        return Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
