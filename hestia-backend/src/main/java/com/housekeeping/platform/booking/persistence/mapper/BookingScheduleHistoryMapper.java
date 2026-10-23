package com.housekeeping.platform.booking.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingScheduleHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
