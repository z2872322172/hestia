package com.housekeeping.platform.scheduling.application;

import com.housekeeping.platform.scheduling.application.contract.SchedulingOperationsStatsService;
import com.housekeeping.platform.scheduling.persistence.mapper.WorkerSlotMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 排班统计契约实现（docs/modules/13 第 18.4 节）。
 *
 * <p>只读聚合，供运营日报任务使用。</p>
 */
@Service
@RequiresPersistence
public class SchedulingStatsService implements SchedulingOperationsStatsService {

    private final WorkerSlotMapper slotMapper;

    public SchedulingStatsService(WorkerSlotMapper slotMapper) {
        this.slotMapper = slotMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotDailyStat> slotDailyStats(LocalDate dateFrom, LocalDate dateTo) {
        return slotMapper.selectSlotDailyStats(dateFrom, dateTo).stream()
                .map(row -> new SlotDailyStat(
                        (LocalDate) row.get("statDate"),
                        ((Number) row.get("cnt")).longValue(),
                        row.get("booked") == null ? 0L : ((Number) row.get("booked")).longValue()))
                .toList();
    }
}
