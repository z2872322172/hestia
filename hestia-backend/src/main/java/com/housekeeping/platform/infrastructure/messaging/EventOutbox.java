package com.housekeeping.platform.infrastructure.messaging;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可靠事件发件箱实体，对应表 {@code event_outbox}。
 *
 * <p>写入必须与业务变更处于同一数据库事务：业务成功但事件没写 = 副作用丢失；
 * 事件写了但业务回滚 = 消费端收到不存在的数据。两者都只能靠同事务解决。</p>
 *
 * <p>本表是只追加表：除 {@code status / retry_count / next_retry_at / last_error / sent_at}
 * 这几个投递状态字段外，事件内容不允许修改。</p>
 */
@Data
@TableName("event_outbox")
public class EventOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一标识（ULID），消费端幂等键。 */
    private String eventId;

    private String eventType;

    private String aggregateType;

    private String aggregateId;

    /** 产生事件时的链路 ID。 */
    private String traceId;

    private String topic;

    private String tag;

    private String messageKey;

    /** 事件载荷 JSON，不含信封字段。 */
    private String payloadJson;

    /** 见 {@link EventOutboxStatus}。 */
    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
