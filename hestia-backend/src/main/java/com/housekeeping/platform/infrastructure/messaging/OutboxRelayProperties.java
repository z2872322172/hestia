package com.housekeeping.platform.infrastructure.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox Relay 配置。
 *
 * <p>重试策略参数化而不是写死：不同事件的重要性不同，通知类可以多试几次，
 * 资金类应尽快转入人工处理，因此把次数与退避基数放到配置里。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.outbox.relay")
public class OutboxRelayProperties {

    /** 是否启用 Relay。 */
    private boolean enabled = true;

    /** 单批最多投递条数，避免一次扫描占用过多连接与内存。 */
    private int batchSize = 20;

    /** 调度间隔（毫秒）。 */
    private long fixedDelayMs = 2000L;

    /** 首次执行延迟（毫秒），留出应用完成初始化与 Flyway 迁移的时间。 */
    private long initialDelayMs = 5000L;

    /** 单条事件最大投递次数，达到后置为 DEAD。 */
    private int maxRetry = 8;

    /** 退避基数（秒）：第 n 次重试等待 base * 2^(n-1)，上限 10 分钟。 */
    private long retryBackoffSeconds = 15L;
}
