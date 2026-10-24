package com.housekeeping.platform.scheduling.application.contract;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 排班模块对运营/统计的只读统计契约（docs/modules/13 第 18.4 节）。
 *
 * <p>时段库存本身不携带区域属性，统计为全公司口径。</p>
 */
public interface SchedulingOperationsStatsService {

    /**
     * 按服务日期统计时段总数与已支付占用数（时段利用率口径）。
     *
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 按日期分组的时段统计
     */
    List<SlotDailyStat> slotDailyStats(LocalDate dateFrom, LocalDate dateTo);

    /**
     * 时段按日统计。
     *
     * @param statDate 统计日期
     * @param total    时段总数
     * @param booked   其中 BOOKED 占用数
     */
    record SlotDailyStat(LocalDate statDate, long total, long booked) {
    }
}
