package com.housekeeping.platform.infrastructure.messaging;

import java.time.OffsetDateTime;

/**
 * 领域事件统一信封。
 *
 * <p>字段与 docs/04 第 10.4 节保持一致。三条约束：</p>
 * <ol>
 *   <li>{@code eventId} 全局唯一，同时作为消费端幂等键；</li>
 *   <li>{@code payload} 只放消费所需快照，禁止放完整地址、证件号和支付密钥；</li>
 *   <li>{@code version} 用于事件结构演进，消费端遇到未知版本应跳过而不是报错重试。</li>
 * </ol>
 *
 * @param eventId       事件唯一标识（ULID）
 * @param eventType     事件类型，如 {@code SCAFFOLD_PING}
 * @param aggregateType 聚合类型，如 {@code BOOKING_ORDER}
 * @param aggregateId   聚合业务 ID
 * @param occurredAt    事件发生时间（含时区偏移）
 * @param traceId       产生事件的链路 ID
 * @param version       事件结构版本，从 1 开始
 * @param payload       事件载荷
 * @param <T>           载荷类型
 */
public record DomainEvent<T>(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        OffsetDateTime occurredAt,
        String traceId,
        int version,
        T payload) {

    /** 当前事件结构版本。 */
    public static final int CURRENT_VERSION = 1;
}
