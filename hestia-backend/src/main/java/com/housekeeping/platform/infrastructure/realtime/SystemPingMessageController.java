package com.housekeeping.platform.infrastructure.realtime;

import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.id.IdGenerator;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.TechEventTypes;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * WebSocket 心跳回声端点（技术冒烟用）。
 *
 * <p>客户端发送到 {@code /app/system.ping}，服务端广播到 {@code /topic/system.ping}。
 * 只回显服务器时间与 traceId，不携带任何业务数据，避免骨架接口被误当作业务接口。</p>
 */
@Controller
public class SystemPingMessageController {

    /**
     * 处理心跳请求。
     *
     * @return 心跳响应
     */
    @MessageMapping("/system.ping")
    @SendTo("/topic/system.ping")
    public SystemPingMessage ping() {
        String traceId = RequestContext.getTraceId();
        return new SystemPingMessage(
                TechEventTypes.SYSTEM_PING,
                "pong",
                LocalDateTime.now().toString(),
                traceId != null ? traceId : IdGenerator.nextId());
    }

    /**
     * 心跳响应体。
     *
     * @param type       消息类型，固定 {@code SYSTEM_PING}
     * @param message    固定 {@code pong}
     * @param serverTime 服务器时间
     * @param traceId    链路标识
     */
    public record SystemPingMessage(String type, String message, String serverTime, String traceId) {
    }
}
