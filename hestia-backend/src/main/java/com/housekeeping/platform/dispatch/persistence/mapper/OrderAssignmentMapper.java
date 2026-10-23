package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.OrderAssignmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单人员分配 Mapper。
 *
 * <p>同一订单不允许两个有效分配：接单/激活/取消全部条件更新，
 * WHERE 带当前状态，影响行数 0 即冲突。</p>
 */
@Mapper
public interface OrderAssignmentMapper extends BaseMapper<OrderAssignmentEntity> {

    /**
     * 支付成功激活：WAITING_PAYMENT → PENDING_ACCEPT 并设置接单截止时间。
     *
     * @param bookingId     订单 ID
     * @param acceptDeadline 接单截止时间
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE order_assignment SET status = 'PENDING_ACCEPT', "
            + "accept_deadline = #{acceptDeadline}, version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status = 'WAITING_PAYMENT'")
    int activateForPayment(@Param("bookingId") Long bookingId,
                           @Param("acceptDeadline") LocalDateTime acceptDeadline);

    /**
     * 取消订单的有效分配（待支付、待接单状态）。
     *
     * @param bookingId 订单 ID
     * @return 影响行数
     */
    @Update("UPDATE order_assignment SET status = 'CANCELED', version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status IN ('WAITING_PAYMENT', 'PENDING_ACCEPT')")
    int cancelForBooking(@Param("bookingId") Long bookingId);

    /**
     * 改派：旧分配 PENDING_ACCEPT → REASSIGNED。
     *
     * @param assignmentId 旧分配记录 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE order_assignment SET status = 'REASSIGNED', version = version + 1 "
            + "WHERE id = #{assignmentId} AND status = 'PENDING_ACCEPT'")
    int reassignFromPending(@Param("assignmentId") Long assignmentId);

    /**
     * 查询订单当前分配（最新一条）。
     *
     * @param bookingId 订单 ID
     * @return 分配记录或 null
     */
    @Select("SELECT * FROM order_assignment WHERE booking_id = #{bookingId} "
            + "ORDER BY id DESC LIMIT 1")
    OrderAssignmentEntity selectLatestByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 查询订单全部分配记录（改派链展示）。
     *
     * @param bookingId 订单 ID
     * @return 分配列表（时间升序）
     */
    @Select("SELECT * FROM order_assignment WHERE booking_id = #{bookingId} ORDER BY id ASC")
    List<OrderAssignmentEntity> selectByBookingId(@Param("bookingId") Long bookingId);
}
