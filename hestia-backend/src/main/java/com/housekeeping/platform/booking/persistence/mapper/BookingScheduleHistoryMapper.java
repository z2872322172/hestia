package com.housekeeping.platform.booking.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingScheduleHistoryEntity;
import com.housekeeping.platform.booking.persistence.query.BookingDailyStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单改期历史 Mapper（只追加）。
 */
@Mapper
public interface BookingScheduleHistoryMapper extends BaseMapper<BookingScheduleHistoryEntity> {

    /**
     * 按订单查询改期历史。
     *
     * @param bookingId 订单 ID
     * @return 历史列表（时间升序）
     */
    @Select("SELECT * FROM booking_schedule_history WHERE booking_id = #{bookingId} "
            + "ORDER BY id ASC")
    List<BookingScheduleHistoryEntity> selectByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 按改期操作发生日 + 区域分组统计改期次数（运营统计契约，docs/modules/13 第 7.1 节）。
     *
     * <p>区域归属通过联订单表获得（只读，不修改 booking_order）。</p>
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 分组统计行，total 为改期次数
     */
    @Select("SELECT DATE(h.created_at) AS statDate, bo.service_area_id AS serviceAreaId, "
            + "COUNT(*) AS total "
            + "FROM booking_schedule_history h "
            + "JOIN booking_order bo ON bo.id = h.booking_id "
            + "WHERE h.created_at >= #{from} AND h.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate, serviceAreaId")
    List<BookingDailyStatRow> selectRescheduledDailyStats(@Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);
}
