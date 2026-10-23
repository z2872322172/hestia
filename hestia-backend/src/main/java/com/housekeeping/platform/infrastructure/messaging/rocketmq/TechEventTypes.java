package com.housekeeping.platform.infrastructure.messaging.rocketmq;

/**
 * 技术阶段的事件类型常量。
 *
 * <p>只用于骨架自检，不参与任何业务统计；业务事件类型在对应模块内定义，
 * 不要往这里堆业务常量，否则技术层会反向依赖业务语义。</p>
 */
public final class TechEventTypes {

    /** 骨架冒烟事件：写 outbox → Relay 投递 → 消费者幂等消费。 */
    public static final String SCAFFOLD_PING = "SCAFFOLD_PING";

    /** 骨架冒烟事件的聚合类型。 */
    public static final String AGGREGATE_SYSTEM = "SYSTEM";

    /** 骨架冒烟事件的消息 Tag。 */
    public static final String TAG_SCAFFOLD = "SCAFFOLD";

    /** WebSocket 心跳消息类型。 */
    public static final String SYSTEM_PING = "SYSTEM_PING";

    private TechEventTypes() {
    }
}
