package com.housekeeping.platform.infrastructure.messaging;

/**
 * 消费幂等记录状态。
 *
 * <p>{@code PROCESSING} 是"已认领但未完成"的中间态：如果进程在消费中途崩溃，
 * 记录会停留在该状态，重投递时不会被视为"已成功消费"而跳过，
 * 但也不会被当成成功记录 —— 需要人工或补偿任务判定，避免消息被静默丢弃。</p>
 */
public enum ConsumerMessageStatus {

    PROCESSING("PROCESSING"),

    SUCCESS("SUCCESS"),

    FAILED("FAILED");

    private final String value;

    ConsumerMessageStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
