package com.housekeeping.platform.infrastructure.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP 配置。
 *
 * <p>技术底座阶段只提供 {@code SYSTEM_PING} 心跳回声，不实现任何业务推送。
 * 后续业务模块的推送地址约定为：</p>
 * <ul>
 *   <li>服务人员个人队列：{@code /user/queue/dispatch}</li>
 *   <li>运营监控广播：{@code /topic/dispatch/monitor}</li>
 * </ul>
 *
 * <p>单实例部署使用 Simple Broker；多实例扩展时改接 Redis Pub/Sub 或外部 Broker，
 * 但不因此拆分微服务（docs/04 第 9.4 节）。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${app.security.cors-allowed-origins}") String corsAllowedOrigins) {
        this.allowedOrigins = corsAllowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 允许的源由配置控制，不使用 "*"：否则任意站点都能借用用户浏览器建立连接
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 简单内存 Broker 负责 /topic 广播；单实例场景足够，且不引入额外中间件
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
