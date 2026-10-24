package com.housekeeping.platform.booking.application.contract;

import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow;
import com.housekeeping.platform.booking.persistence.query.BookingExportRow;
import com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow;
import com.housekeeping.platform.common.api.PageResult;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 订单模块对运营/统计的只读统计契约（docs/modules/13 第 18.1 节）。
 *
 * <p>运营模块聚合订单数据必须走本契约，禁止直接读 {@code booking_order}。
 * 按日统计方法返回<b>全部区域</b>的分组行，由调用方（日报任务 / 看板）
 * 结合自己的数据范围裁剪——统计聚合是系统任务，不携带运营人员视角。</p>
 */
public interface BookingOperationsStatsService {

    /**
     * 按订单创建日统计（含当日创建订单的完成 / 取消终态）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期 + 区域分组的统计行
     */
    List<BookingDailyStatRow> createdDailyStats(LocalDate from, LocalDate to);

    /**
     * 按支付完成日统计实付笔数与金额（paid_at 归属）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期 + 区域分组的金额统计行
     */
    List<BookingPaidStatRow> paidDailyStats(LocalDate from, LocalDate to);

    /**
     * 按改期操作发生日统计改期次数（booking_schedule_history 归属）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期 + 区域分组的改期次数
     */
    List<BookingDailyStatRow> rescheduledDailyStats(LocalDate from, LocalDate to);

    /**
     * 运营看板"今日"计数。
     *
     * <p>口径：均为"创建日 = 统计日"的订单，其中待支付为当前仍处于
     * PENDING_PAYMENT 的订单数，完成 / 取消为当前终态计数。</p>
     *
     * @param areaIds 授权服务区域；空集合 = 不限区域（ADMIN 或全公司口径）
     * @param date    统计日期
     * @return 今日计数
     */
    BookingTodayStat todayStat(Collection<Long> areaIds, LocalDate date);

    /**
     * 运营订单监控列表（带区域数据范围过滤）。
     *
     * @param areaIds         授权服务区域；空集合 = 不限区域
     * @param status          主状态过滤，可空
     * @param appointmentDate 服务日期过滤，可空
     * @param page            页码
     * @param size            每页大小
     * @return 分页结果
     */
    PageResult<BookingSummaryView> monitorOrders(Collection<Long> areaIds, String status,
                                                 LocalDate appointmentDate, int page, int size);

    /**
     * 订单导出行（docs/modules/13 第 11 节，仅限 ORDERS 导出类型）。
     *
     * @param areaIds 授权服务区域；空集合 = 不限区域
     * @param from    创建起始日期（含）
     * @param to      创建结束日期（含）
     * @param limit   最大行数，防止大范围导出拖垮库
     * @return 导出行列表
     */
    List<BookingExportRow> exportRows(Collection<Long> areaIds, LocalDate from, LocalDate to, int limit);

    /**
     * 看板"今日"计数。
     *
     * @param created        今日创建订单数
     * @param pendingPayment 今日创建且当前仍待支付的订单数
     * @param completed      今日创建且已完成的订单数
     * @param canceled       今日创建且已取消的订单数
     */
    record BookingTodayStat(long created, long pendingPayment, long completed, long canceled) {
    }
}
