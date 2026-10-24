package com.housekeeping.platform.fulfillment.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 客户自动确认任务（docs/modules/09 第 11.3 节）。
 *
 * <p>扫描确认截止时间已过仍处于 PENDING_CONFIRM 的履约记录，以 SYSTEM 来源
 * 完成确认并推进订单主状态。幂等由条件更新保证：客户恰好同时确认时
 * 只有 UPDATE ... WHERE status = 'PENDING_CONFIRM' 的一个成功。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class FulfillmentAutoConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentAutoConfirmJob.class);

    /** 单批处理上限。 */
    private static final int BATCH_SIZE = 50;

    private final FulfillmentApplicationService fulfillmentService;

    public FulfillmentAutoConfirmJob(FulfillmentApplicationService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * 定时扫描。
     */
    @Scheduled(fixedDelayString = "${app.fulfillment.auto-confirm-sweep-interval-ms:30000}",
            initialDelayString = "${app.fulfillment.auto-confirm-sweep-interval-ms:30000}")
    public void scheduledSweep() {
        int confirmed = runOnce();
        if (confirmed > 0) {
            log.info("fulfillment auto-confirm sweep completed {} record(s)", confirmed);
        }
    }

    /**
     * 执行一轮扫描（冒烟脚本手动触发复用）。
     *
     * @return 本轮确认数
     */
    public int runOnce() {
        return fulfillmentService.autoConfirmDue(LocalDateTime.now(), BATCH_SIZE);
    }

    /**
     * 以指定时刻执行一轮扫描（冒烟脚本注入模拟时钟）。
     *
     * @param asOf 视作当前的时刻
     * @return 本轮确认数
     */
    public int runOnce(LocalDateTime asOf) {
        return fulfillmentService.autoConfirmDue(asOf, BATCH_SIZE);
    }
}
