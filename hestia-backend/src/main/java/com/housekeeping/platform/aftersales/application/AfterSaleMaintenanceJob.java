package com.housekeeping.platform.aftersales.application;

import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleEventEntity;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleTicketEntity;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleEventMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleTicketMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后维护任务（docs/modules/11 第 4.4 节）。
 *
 * <p>系统侧职责：标记超时工单（SLA）+ 定时兜底重算质量汇总。
 * 超时标记幂等——同一工单只写一条 SLA_EXPIRED 事件。</p>
 */
@Component
@RequiresPersistence
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled",
        havingValue = "true", matchIfMissing = true)
public class AfterSaleMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(AfterSaleMaintenanceJob.class);

    private static final int OVERDUE_BATCH = 200;

    private final AfterSaleTicketMapper ticketMapper;

    private final AfterSaleEventMapper eventMapper;

    private final WorkerQualityService workerQualityService;

    public AfterSaleMaintenanceJob(AfterSaleTicketMapper ticketMapper,
                                   AfterSaleEventMapper eventMapper,
                                   WorkerQualityService workerQualityService) {
        this.ticketMapper = ticketMapper;
        this.eventMapper = eventMapper;
        this.workerQualityService = workerQualityService;
    }

    /**
     * 定时扫描：SLA 超时标记 + 质量汇总兜底重算。
     */
    @Scheduled(fixedDelayString = "${app.aftersale.maintenance-interval-ms:60000}")
    public void run() {
        try {
            runOnce(LocalDateTime.now());
        } catch (Exception exception) {
            // 定时任务异常不中断调度，记录后等待下一轮
            log.error("after-sale maintenance failed", exception);
        }
    }

    /**
     * 单次维护（冒烟脚本手动触发入口）。
     *
     * @param asOf 时间基准
     * @return 本次标记的超时工单数
     */
    public int runOnce(LocalDateTime asOf) {
        int marked = markOverdue(asOf);
        int recalculated = workerQualityService.recalculateAllExisting();
        log.debug("after-sale maintenance done, marked={}, recalculated={}", marked, recalculated);
        return marked;
    }

    private int markOverdue(LocalDateTime asOf) {
        List<AfterSaleTicketEntity> overdue = ticketMapper.selectOverdue(asOf, OVERDUE_BATCH);
        for (AfterSaleTicketEntity ticket : overdue) {
            if (eventMapper.existsEvent(ticket.getId(), "SLA_EXPIRED")) {
                continue;
            }
            AfterSaleEventEntity event = new AfterSaleEventEntity();
            event.setTicketId(ticket.getId());
            event.setEventType("SLA_EXPIRED");
            event.setFromStatus(ticket.getStatus());
            event.setToStatus(ticket.getStatus());
            event.setDescription("工单已超出处理时限，请尽快跟进");
            event.setOperatorRole("SYSTEM");
            eventMapper.insert(event);
        }
        return overdue.size();
    }
}
