package com.housekeeping.platform.booking.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingCancellationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单取消记录 Mapper（只追加，每单最多一条）。
 */
@Mapper
public interface BookingCancellationMapper extends BaseMapper<BookingCancellationEntity> {

    /**
     * 按订单查询取消记录。
     *
     * @param bookingId 订单 ID
     * @return 取消记录或 null
     */
    @Select("SELECT * FROM booking_cancellation WHERE booking_id = #{bookingId}")
    BookingCancellationEntity selectByBookingId(@Param("bookingId") Long bookingId);
}
