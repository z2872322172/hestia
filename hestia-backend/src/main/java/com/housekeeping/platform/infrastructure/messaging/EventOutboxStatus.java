package com.housekeeping.platform.infrastructure.messaging;

/**
 * 事件发件箱状态机。
 *
 * <pre>
 * PENDING --claim--> SENDING --send ok--> SENT
 *                       |
 *                       +--send fail--> PENDING（未超次数，等退避）
 *                                       FAILED（本批次失败但可重试）
 *                                       DEAD（超过最大重试次数，需人工处理）
 * </pre>
 *
 * <p>{@code SENDING} 是"已被某个 Relay 实例领走"的中间态，用于防止多实例重复投递；
 * 进程崩溃导致的悬挂 SENDING 由后续版本的补偿任务回收。</p>
 */
public enum EventOutboxStatus {

    /** 待投递。 */
    PENDING("PENDING"),

    /** 已被 Relay 领取，正在投递。 */
    SENDING("SENDING"),

    /** 投递成功。 */
    SENT("SENT"),

    /** 投递失败，等待退避重试。 */
    FAILED("FAILED"),

    /** 超过最大重试次数，转入人工处理。 */
    DEAD("DEAD");

    private final String value;

    EventOutboxStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
