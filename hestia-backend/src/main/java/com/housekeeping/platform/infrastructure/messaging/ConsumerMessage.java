package com.housekeeping.platform.infrastructure.messaging;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消费幂等记录实体，对应表 {@code consumer_message}。
 *
 * <p>唯一键 {@code (consumer_group, message_id)} 是"同一消息只处理一次"的最终保证：
 * 应用层先查后插在并发下会失效，数据库唯一索引不会。</p>
 */
@Data
@TableName("consumer_message")
public class ConsumerMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String consumerGroup;

    /** 消息 ID；本项目统一使用事件 eventId，保证重复投递时取值稳定。 */
    private String messageId;

    private String eventType;

    private String aggregateId;

    /** 见 {@link ConsumerMessageStatus}。 */
    private String status;

    private String errorMessage;

    private LocalDateTime consumedAt;

    private LocalDateTime updatedAt;
}
