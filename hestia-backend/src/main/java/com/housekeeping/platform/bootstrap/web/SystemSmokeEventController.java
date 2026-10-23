package com.housekeeping.platform.bootstrap.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.housekeeping.platform.bootstrap.web.vo.SmokeEventStatusVo;
import com.housekeeping.platform.bootstrap.web.vo.SmokeEventVo;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.infrastructure.messaging.ConsumerIdempotencyService;
import com.housekeeping.platform.infrastructure.messaging.ConsumerMessage;
import com.housekeeping.platform.infrastructure.messaging.EventOutbox;
import com.housekeeping.platform.infrastructure.messaging.OutboxQueryService;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.RocketMqProperties;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.SmokeEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 事件链路冒烟接口：验证 {@code outbox → Relay → RocketMQ → 幂等消费} 全链路。
 *
 * <p>只在 {@code app.security.smoke-api-enabled=true} 的 profile 下可访问（local / demo / test），
 * 生产环境由安全配置统一 denyAll。</p>
 */
@RestController
@RequestMapping("/api/v1/system/smoke")
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "系统冒烟-事件", description = "写入发件箱事件并查询投递与消费状态")
public class SystemSmokeEventController {

    private final SmokeEventService smokeEventService;

    private final OutboxQueryService outboxQueryService;

    private final ConsumerIdempotencyService consumerIdempotencyService;

    private final RocketMqProperties rocketMqProperties;

    public SystemSmokeEventController(SmokeEventService smokeEventService,
                                      OutboxQueryService outboxQueryService,
                                      ConsumerIdempotencyService consumerIdempotencyService,
                                      RocketMqProperties rocketMqProperties) {
        this.smokeEventService = smokeEventService;
        this.outboxQueryService = outboxQueryService;
        this.consumerIdempotencyService = consumerIdempotencyService;
        this.rocketMqProperties = rocketMqProperties;
    }

    /**
     * 写入一条 {@code SCAFFOLD_PING} 事件。
     *
     * <p>返回时事件只处于 PENDING，真正的投递由 Relay 异步完成；
     * 调用方需要用返回的 eventId 轮询状态接口，才能确认链路是否走通。</p>
     *
     * @return 事件 ID 与初始状态
     */
    @PostMapping("/event")
    @Operation(summary = "写入冒烟事件", description = "落 event_outbox，返回 eventId；由 Relay 异步投递 RocketMQ")
    public Result<SmokeEventVo> publishEvent() {
        try {
            EventOutbox event = smokeEventService.publishScaffoldPing();
            return Result.ok(new SmokeEventVo(event.getEventId(), event.getStatus(), event.getTopic()));
        } catch (JsonProcessingException exception) {
            // 载荷序列化失败说明代码有缺陷，不应把内部异常直接抛给调用方
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "事件载荷序列化失败", exception);
        }
    }

    /**
     * 查询冒烟事件的全链路状态。
     *
     * @param eventId 事件 ID
     * @return 发件箱状态与消费状态
     */
    @GetMapping("/event/{eventId}")
    @Operation(summary = "查询冒烟事件状态", description = "同时返回 outbox 投递状态与消费端幂等记录")
    public Result<SmokeEventStatusVo> eventStatus(@PathVariable("eventId") String eventId) {
        EventOutbox event = outboxQueryService.findByEventId(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "事件不存在：" + eventId));

        String consumerGroup = rocketMqProperties.getConsumerGroups().getTechSmoke();
        Optional<ConsumerMessage> consumerMessage = consumerIdempotencyService.find(consumerGroup, eventId);

        return Result.ok(new SmokeEventStatusVo(
                event.getEventId(),
                event.getEventType(),
                event.getStatus(),
                event.getRetryCount() == null ? 0 : event.getRetryCount(),
                event.getLastError(),
                event.getSentAt() == null ? null : event.getSentAt().toString(),
                consumerMessage.map(message -> "SUCCESS".equals(message.getStatus())).orElse(false),
                consumerGroup,
                consumerMessage.map(message -> message.getConsumedAt().toString()).orElse(null)));
    }
}
