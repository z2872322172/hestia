package com.housekeeping.platform.aftersales.application.contract;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 售后模块对运营/统计的只读统计契约（docs/modules/13 第 18.3 节）。
 */
public interface AftersalesOperationsStatsService {

    /**
     * 按工单提交日统计投诉 / 售后工单数（被驳回的不计）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期 + 区域分组计数
     */
    List<AreaDailyCount> complaintDaily(LocalDate from, LocalDate to);

    /**
     * 按工单提交日统计返工工单数（存在返工订单引用的工单）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期 + 区域分组计数
     */
    List<AreaDailyCount> reworkDaily(LocalDate from, LocalDate to);

    /**
     * 未结工单数（运营工作台"待处理投诉"）。
     *
     * @param areaIds 授权服务区域；空集合 = 不限区域（ADMIN）
     * @return 未结工单数
     */
    long openTicketCount(Collection<Long> areaIds);

    /**
     * 区域按日计数。
     *
     * @param statDate      统计日期
     * @param serviceAreaId 服务区域 ID
     * @param cnt           计数
     */
    record AreaDailyCount(LocalDate statDate, Long serviceAreaId, long cnt) {
    }
}
