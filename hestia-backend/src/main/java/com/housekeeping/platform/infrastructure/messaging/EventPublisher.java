package com.housekeeping.platform.infrastructure.messaging;

/**
 * 事件投递端口。
 *
 * <p>Relay 只依赖该端口，不关心底层是 RocketMQ 还是其他实现。
 * 这样做的好处：本地没有 Broker 时可以用日志实现验证 Relay 逻辑，
 * 未来更换消息中间件也不需要改 Relay。</p>
 */
public interface EventPublisher {

    /**
     * 投递一条发件箱事件。
     *
     * @param event 已持久化的事件
     * @throws EventPublishException 投递失败，调用方据此计算退避重试
     */
    void publish(EventOutbox event);
}
