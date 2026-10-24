package com.housekeeping.platform.notification.domain;

import java.time.LocalDateTime;

/**
 * 通知任务重试退避纯函数（docs/modules/12 第 14 节）。
 *
 * <p>退避序列：第 1 次失败后 1 分钟、第 2 次 5 分钟、第 3 次 30 分钟、
 * 第 4 次 2 小时；第 5 次失败不再重试，任务停留 FAILED 等人工处理。
 * 不依赖 Spring 与数据库，便于穷举单测钉死边界。</p>
 */
public final class NotificationRetryBackoff {

    /** 每次失败后到下次重试的间隔（分钟），下标 = 已失败次数 - 1。 */
    private static final long[] BACKOFF_MINUTES = {1L, 5L, 30L, 120L};

    private NotificationRetryBackoff() {
    }

    /**
     * 计算第 {@code failedCount} 次失败后的下次重试时刻。
     *
     * <p>序列外的失败次数（理论上到不了，防御性兜底）按最后一档 2 小时处理，
     * 保证任何输入都有确定的下一时刻，不会出现 NullPointerException。</p>
     *
     * @param failedCount 已连续失败的次数（从 1 开始）
     * @param from        计算基准时刻
     * @return 下次允许重试的时刻
     */
    public static LocalDateTime nextRetryAt(int failedCount, LocalDateTime from) {
        int index = Math.max(failedCount, 1) - 1;
        if (index >= BACKOFF_MINUTES.length) {
            index = BACKOFF_MINUTES.length - 1;
        }
        return from.plusMinutes(BACKOFF_MINUTES[index]);
    }

    /**
     * 判断失败次数是否已耗尽（达到最大重试次数，进入人工处理）。
     *
     * <p>边界约定：{@code maxRetryCount} 表示<b>允许的失败次数上限</b>，
     * 第 5 次失败（retryCount 已到 5）即为耗尽——与表 CHECK 约束
     * {@code retry_count <= max_retry_count} 对齐，不会出现第 6 次写库。</p>
     *
     * @param retryCount    已失败次数
     * @param maxRetryCount 最大允许失败次数
     * @return true 表示已耗尽，不能再重试
     */
    public static boolean exhausted(int retryCount, int maxRetryCount) {
        return retryCount >= maxRetryCount;
    }
}
