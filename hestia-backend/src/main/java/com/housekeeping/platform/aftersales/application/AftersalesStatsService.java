package com.housekeeping.platform.aftersales.application;

import com.housekeeping.platform.aftersales.application.contract.AftersalesOperationsStatsService;
import com.housekeeping.platform.aftersales.persistence.mapper.AfterSaleTicketMapper;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 售后统计契约实现（docs/modules/13 第 18.3 节）。
 *
 * <p>只读聚合，供运营日报任务与看板使用。</p>
 */
@Service
@RequiresPersistence
public class AftersalesStatsService implements AftersalesOperationsStatsService {

    private final AfterSaleTicketMapper ticketMapper;

    public AftersalesStatsService(AfterSaleTicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AreaDailyCount> complaintDaily(LocalDate from, LocalDate to) {
        return toCounts(ticketMapper.selectComplaintDaily(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AreaDailyCount> reworkDaily(LocalDate from, LocalDate to) {
        return toCounts(ticketMapper.selectReworkDaily(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public long openTicketCount(Collection<Long> areaIds) {
        return ticketMapper.countOpen(areaIds);
    }

    private List<AreaDailyCount> toCounts(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new AreaDailyCount(
                        (LocalDate) row.get("statDate"),
                        (Long) row.get("serviceAreaId"),
                        ((Number) row.get("cnt")).longValue()))
                .toList();
    }
}
