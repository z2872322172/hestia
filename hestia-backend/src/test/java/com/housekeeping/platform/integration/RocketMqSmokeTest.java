package com.housekeeping.platform.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RocketMQ 链路冒烟测试（占位）。
 *
 * <p>为什么暂时禁用：RocketMQ 5.x 的 gRPC 客户端需要 NameServer + Broker + Proxy 三个容器
 * 才能形成可用集群，Testcontainers 没有官方的 5.x 组合模块；本机当前也没有 Docker，
 * 写一个无法验证的容器编排测试只会制造"看起来有测试、实际没跑过"的假象。</p>
 *
 * <p>当前替代验证方式：启动 compose 后用
 * {@code scripts/dev/smoke-test.ps1} 走完整链路
 * （POST /api/v1/system/smoke/event → Relay 投递 → 消费者写 consumer_message → 状态接口返回 consumed=true）。</p>
 *
 * <p>TODO（批次 3 之前）：在有 Docker 的 CI 上补齐该测试，触发条件是引入订单事件时。</p>
 */
@Tag("integration")
@Disabled("RocketMQ 5.x 需要 NameServer + Broker + Proxy 三容器，当前环境无 Docker，改用 smoke-test.ps1 验证")
class RocketMqSmokeTest {

    @Test
    void scaffoldPingEventIsPublishedAndConsumed() {
        // 计划实现：
        // 1. 用 compose 启动 RocketMQ，指向 localhost:8081；
        // 2. 通过 SmokeEventService 写 outbox；
        // 3. 调用 OutboxRelayService.relayOnce()；
        // 4. 轮询 ConsumerIdempotencyService.find(GID_TECH_SMOKE, eventId) 断言 SUCCESS。
    }
}
