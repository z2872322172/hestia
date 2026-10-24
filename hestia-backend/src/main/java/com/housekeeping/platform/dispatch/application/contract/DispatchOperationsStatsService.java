package com.housekeeping.platform.dispatch.application.contract;

import com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 派单模块对运营/统计的只读统计契约（docs/modules/13 第 18.2 节）。
 *
 * <p>派单行为本身不携带区域属性，按日统计为全公司口径；
 * 看板计数（待接单 / 待人工）同样是全局队列指标。</p>
 */
public interface DispatchOperationsStatsService {

    /**
     * 自动派单接单成功数，按接单日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    List<AssignmentDateCountRow> autoSuccessDaily(LocalDate from, LocalDate to);

    /**
     * 拒单数，按拒单日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    List<AssignmentDateCountRow> rejectedDaily(LocalDate from, LocalDate to);

    /**
     * 接单超时数，按失效日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    List<AssignmentDateCountRow> timeoutDaily(LocalDate from, LocalDate to);

    /**
     * 人工派单数，按派单日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    List<AssignmentDateCountRow> manualDaily(LocalDate from, LocalDate to);

    /**
     * 运营工作台派单计数：待接单（PENDING_ACCEPT）与待人工（MANUAL_REQUIRED）。
     *
     * <p>{@code areaIds} 参数保留签名一致性；派单队列为全局指标，区域过滤不生效。</p>
     *
     * @param areaIds 授权服务区域（当前口径不使用）
     * @return 派单队列计数
     */
    DispatchQueueStat queueStat(Collection<Long> areaIds);

    /**
     * 派单队列计数。
     *
     * @param pendingAccept 待接单任务数
     * @param manualRequired 待人工处理任务数
     */
    record DispatchQueueStat(long pendingAccept, long manualRequired) {
    }
}
