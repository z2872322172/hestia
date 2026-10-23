package com.housekeeping.platform.infrastructure.messaging;

/**
 * 事件投递失败异常。
 *
 * <p>只用于表达"消息没有成功发出"，不携带 Broker 的完整响应体，
 * 避免把内部端点、队列名等实现细节写进数据库的 {@code last_error} 字段。</p>
 */
public class EventPublishException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventPublishException(String message) {
        super(message);
    }
}
