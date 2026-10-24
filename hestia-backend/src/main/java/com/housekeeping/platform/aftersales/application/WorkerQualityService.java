package com.housekeeping.platform.aftersales.application;

import com.housekeeping.platform.aftersales.application.view.WorkerQualitySummaryView;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleTicketEntity;
import com.housekeeping.platform.aftersales.persistence.entity.ServiceReviewEntity;
import com.housekeeping.platform.aftersales.persistence.entity.WorkerQualityStatEntity;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleTicketMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.ServiceReviewMapper;
import com.housekeeping.platform.aftersales.persistence.mapper.WorkerQualityStatMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 人员质量汇总服务（docs/modules/11 第 8 节）。
 *
 * <p>汇总表可全量幂等重算：评价提交、售后形成结论后事件驱动重算，
 * 定时任务对已有汇总兜底重算。只供派单评分与运营查看，
 * 不作为自动处罚依据。</p>
 */
@Service
@RequiresPersistence
public class WorkerQualityService {

    private static final Logger log = LoggerFactory.getLogger(WorkerQualityService.class);

    /** 质量类投诉工单类型（计入 complaint_count）。 */
    private static final Set<String> COMPLAINT_TYPES =
            Set.of("QUALITY_COMPLAINT", "SERVICE_ATTITUDE", "DAMAGE");

    /** 无可见评价时的平均分（与表默认值口径一致，0-5）。 */
    private static final BigDecimal DEFAULT_AVERAGE = BigDecimal.valueOf(5).setScale(2);

    private final ServiceReviewMapper reviewMapper;

    private final AfterSaleTicketMapper ticketMapper;

    private final WorkerQualityStatMapper statMapper;

    private final BookingOrderMapper bookingOrderMapper;

    public WorkerQualityService(ServiceReviewMapper reviewMapper,
                                AfterSaleTicketMapper ticketMapper,
                                WorkerQualityStatMapper statMapper,
                                BookingOrderMapper bookingOrderMapper) {
        this.reviewMapper = reviewMapper;
        this.ticketMapper = ticketMapper;
        this.statMapper = statMapper;
        this.bookingOrderMapper = bookingOrderMapper;
    }

    /**
     * 幂等重算某人员的质量汇总（评价 / 售后结论事件后调用）。
     *
     * @param workerId 人员 ID
     */
    @Transactional
    public void recalculate(long workerId) {
        List<ServiceReviewEntity> visibleReviews = reviewMapper.selectVisibleScoresByWorker(workerId);
        int reviewCount = visibleReviews.size();
        BigDecimal average = reviewCount == 0 ? DEFAULT_AVERAGE
                : visibleReviews.stream()
                        .map(ServiceReviewEntity::getOverallScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP);

        long completed = bookingOrderMapper.countCompletedByWorker(workerId);

        int complaint = 0;
        int late = 0;
        int rework = 0;
        for (AfterSaleTicketEntity ticket : ticketMapper.selectNonRejectedByWorker(workerId)) {
            String type = ticket.getTicketType();
            if (COMPLAINT_TYPES.contains(type)) {
                complaint++;
            } else if ("LATE_ARRIVAL".equals(type)) {
                late++;
            } else if ("REWORK".equals(type)) {
                rework++;
            }
        }

        if (statMapper.updateStat(workerId, reviewCount, average, (int) completed,
                complaint, late, rework) == 0) {
            WorkerQualityStatEntity stat = new WorkerQualityStatEntity();
            stat.setWorkerId(workerId);
            stat.setReviewCount(reviewCount);
            stat.setAverageRating(average);
            stat.setCompletedOrderCount((int) completed);
            stat.setComplaintCount(complaint);
            stat.setLateCount(late);
            stat.setReworkCount(rework);
            stat.setLastCalculatedAt(LocalDateTime.now());
            try {
                statMapper.insert(stat);
            } catch (DuplicateKeyException exception) {
                // 并发首次重算：唯一键兜底，退化为更新
                statMapper.updateStat(workerId, reviewCount, average, (int) completed,
                        complaint, late, rework);
            }
        }
        log.debug("quality stat recalculated, workerId={}, reviews={}, avg={}, completed={}",
                workerId, reviewCount, average, completed);
    }

    /**
     * 查询人员质量汇总（无记录返回中性默认视图）。
     *
     * @param workerId 人员 ID
     * @return 汇总视图
     */
    @Transactional(readOnly = true)
    public WorkerQualitySummaryView summary(long workerId) {
        WorkerQualityStatEntity stat = statMapper.selectByWorkerId(workerId);
        return stat == null ? WorkerQualitySummaryView.empty(workerId)
                : WorkerQualitySummaryView.from(stat);
    }

    /**
     * 重算全部已有汇总的人员（定时兜底任务用）。
     *
     * @return 重算人数
     */
    @Transactional
    public int recalculateAllExisting() {
        List<Long> workerIds = statMapper.selectAllWorkerIds();
        workerIds.forEach(this::recalculate);
        return workerIds.size();
    }
}
