package com.housekeeping.platform.booking.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单金额明细 Mapper。
 */
@Mapper
public interface BookingItemMapper extends BaseMapper<BookingItemEntity> {

    /**
     * 按订单查询明细（按写入顺序）。
     *
     * @param bookingId 订单 ID
     * @return 明细列表
     */
    @Select("SELECT * FROM booking_item WHERE booking_id = #{bookingId} "
            + "ORDER BY sort_order ASC, id ASC")
    List<BookingItemEntity> selectByBookingId(@Param("bookingId") Long bookingId);
}
