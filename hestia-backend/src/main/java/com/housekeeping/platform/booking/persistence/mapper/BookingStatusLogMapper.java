package com.housekeeping.platform.booking.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingStatusLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单主状态日志 Mapper（只追加）。
 */
@Mapper
public interface BookingStatusLogMapper extends BaseMapper<BookingStatusLogEntity> {

    /**
     * 按订单查询状态日志。
     *
     * @param bookingId 订单 ID
     * @return 日志列表（时间升序）
     */
    @Select("SELECT * FROM booking_status_log WHERE booking_id = #{bookingId} "
            + "ORDER BY id ASC")
    List<BookingStatusLogEntity> selectByBookingId(@Param("bookingId") Long bookingId);
}
