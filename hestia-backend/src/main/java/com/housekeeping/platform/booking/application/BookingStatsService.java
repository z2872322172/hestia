package com.housekeeping.platform.booking.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingScheduleHistoryMapper;
import com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow;
import com.housekeeping.platform.booking.persistence.query.BookingExportRow;
import com.housekeeping.platform.booking.persistence.query.BookingPaidStatRow;
import com.housekeeping.platform.booking.persistence.query.BookingTodayStatRow;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 订单统计契约实现（docs/modules/13 第 18.1 节）。
 *
 * <p>只读聚合：所有方法 {@code readOnly = true}，供运营日报任务、看板和导出使用。
 * 区域裁剪策略：按日统计返回全部区域的分组行（系统任务无人员视角），
 * 看板 / 监控 / 导出按调用方传入的授权区域过滤，空集合 = 不限区域（ADMIN）。</p>
 */
@Service
@RequiresPersistence
public class BookingStatsService implements BookingOperationsStatsService {

    private final BookingOrderMapper orderMapper;

    private final BookingScheduleHistoryMapper scheduleHistoryMapper;

    public BookingStatsService(BookingOrderMapper orderMapper,
                               BookingScheduleHistoryMapper scheduleHistoryMapper) {
        this.orderMapper = orderMapper;
        this.scheduleHistoryMapper = scheduleHistoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingDailyStatRow> createdDailyStats(LocalDate from, LocalDate to) {
        return orderMapper.selectCreatedDailyStats(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingPaidStatRow> paidDailyStats(LocalDate from, LocalDate to) {
        return orderMapper.selectPaidDailyStats(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingDailyStatRow> rescheduledDailyStats(LocalDate from, LocalDate to) {
        return scheduleHistoryMapper.selectRescheduledDailyStats(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingTodayStat todayStat(Collection<Long> areaIds, LocalDate date) {
        BookingTodayStatRow row = orderMapper.selectTodayStat(areaIds, date);
        if (row == null) {
            return new BookingTodayStat(0, 0, 0, 0);
        }
        return new BookingTodayStat(
                nvl(row.getTotal()),
                nvl(row.getPendingPayment()),
                nvl(row.getCompleted()),
                nvl(row.getCanceled()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BookingSummaryView> monitorOrders(Collection<Long> areaIds, String status,
                                                        LocalDate appointmentDate, int page, int size) {
        LambdaQueryWrapper<BookingOrderEntity> wrapper = new LambdaQueryWrapper<BookingOrderEntity>()
                .in(areaIds != null && !areaIds.isEmpty(),
                        BookingOrderEntity::getServiceAreaId, areaIds)
                .eq(status != null && !status.isBlank(), BookingOrderEntity::getOrderStatus, status)
                .eq(appointmentDate != null, BookingOrderEntity::getAppointmentDate, appointmentDate)
                .orderByDesc(BookingOrderEntity::getCreatedAt);
        Page<BookingOrderEntity> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        List<BookingSummaryView> records = result.getRecords().stream()
                .map(order -> new BookingSummaryView(order.getOrderNo(),
                        order.getServiceNameSnapshot(), order.getOrderStatus(),
                        order.getPaymentStatus(), order.getAppointmentDate(),
                        order.getAppointmentStart(), order.getPayableAmount(),
                        order.getCreatedAt()))
                .toList();
        return new PageResult<>(records, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingExportRow> exportRows(Collection<Long> areaIds, LocalDate from, LocalDate to,
                                             int limit) {
        return orderMapper.selectExportRows(areaIds, from, to, limit);
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
