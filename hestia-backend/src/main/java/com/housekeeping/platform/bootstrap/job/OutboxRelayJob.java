package com.housekeeping.platform.bootstrap.job;

import com.housekeeping.platform.infrastructure.messaging.OutboxRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox Relay 定时任务。
 *
 * <p>调度器只做触发，不含任何业务逻辑；真正的领取、投递、退避都在应用服务里，
 * 这样任务可以被集成测试直接调用而不依赖调度线程。</p>
 *
 * <p>单实例部署用 Spring Scheduler 即可；多实例时靠
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} 保证同一事件不被重复领取，
 * 不需要额外引入分布式调度框架（docs/03 第 3.3 节明确不选 XXL-Job）。</p>
 */
@Component
@ConditionalOnExpression("${app.outbox.relay.enabled:true} and ${app.feature.persistence-enabled:true} and ${app.rocketmq.enabled:true}")
public class OutboxRelayJob {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final OutboxRelayService outboxRelayService;

    public OutboxRelayJob(OutboxRelayService outboxRelayService) {
        this.outboxRelayService = outboxRelayService;
    }

    /**
     * 按固定延迟执行一轮投递。
     *
     * <p>使用 fixedDelay 而不是 fixedRate：上一轮没跑完时不应叠加新的一轮，
     * 否则 Broker 变慢会把线程池和数据库连接同时压满。</p>
     */
    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:2000}",
            initialDelayString = "${app.outbox.relay.initial-delay-ms:5000}")
    public void relay() {
        try {
            int delivered = outboxRelayService.relayOnce();
            if (delivered > 0) {
                LOG.debug("Outbox Relay 投递成功 {} 条", delivered);
            }
        } catch (Exception exception) {
            // 定时任务必须自己兜住异常，否则一次数据库抖动会让后续调度全部停止
            LOG.error("Outbox Relay 执行异常", exception);
        }
    }
}
