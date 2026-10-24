package com.housekeeping.platform.dispatch.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 接单超时自动改派任务（docs/modules/08 第 8.4 节）。
 *
 * <p>扫描接单截止时间已过仍处于 PENDING_ACCEPT 的分配，逐条执行与
 * "拒单"相同的链路：EXPIRED → 释放时段 → REASSIGNING → 下一候选
 * （无候选转 MANUAL_REQUIRED）。单单容错：一条分配的异常不阻断同批，
 * 条件更新兜底人员恰好同时接单/拒单的并发。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class DispatchAcceptTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(DispatchAcceptTimeoutJob.class);

    /** 单批处理上限。 */
    private static final int BATCH_SIZE = 50;

    private final DispatchApplicationService dispatchService;

    public DispatchAcceptTimeoutJob(DispatchApplicationService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /**
     * 定时扫描。
     */
    @Scheduled(fixedDelayString = "${app.dispatch.accept-timeout-sweep-interval-ms:30000}",
            initialDelayString = "${app.dispatch.accept-timeout-sweep-interval-ms:30000}")
    public void scheduledSweep() {
        int handled = runOnce();
        if (handled > 0) {
            log.info("dispatch accept timeout sweep handled {} assignment(s)", handled);
        }
    }

    /**
     * 执行一轮扫描（冒烟脚本手动触发复用）。
     *
     * @return 本轮处理的分配数
     */
    public int runOnce() {
        return dispatchService.expireTimeouts(LocalDateTime.now(), BATCH_SIZE);
    }

    /**
     * 以指定时刻执行一轮扫描（冒烟脚本注入模拟时钟，无需真实等待超时）。
     *
     * @param asOf 视作当前的时刻
     * @return 本轮处理的分配数
     */
    public int runOnce(LocalDateTime asOf) {
        return dispatchService.expireTimeouts(asOf, BATCH_SIZE);
    }
}
