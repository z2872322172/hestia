package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 派单任务 Mapper。
 *
 * <p>状态流转一律条件更新：WHERE booking_id + 当前状态，影响行数 0 即冲突。</p>
 */
@Mapper
public interface DispatchJobMapper extends BaseMapper<DispatchJobEntity> {

    /**
     * 支付成功激活：WAITING_PAYMENT → PENDING_ACCEPT（docs/modules/08 第 8.2 节）。
     *
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE dispatch_job SET status = 'PENDING_ACCEPT', version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status = 'WAITING_PAYMENT'")
    int activateForPayment(@Param("bookingId") Long bookingId);

    /**
     * 取消派单任务（订单取消时调用）。
     *
     * <p>待支付、就绪、待接单、改派中的任务都可以直接取消；
     * ACCEPTED 之后的任务随履约处理，不走本更新。</p>
     *
     * @param bookingId 订单 ID
     * @return 影响行数
     */
    @Update("UPDATE dispatch_job SET status = 'CANCELED', version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status IN "
            + "('WAITING_PAYMENT', 'READY', 'PENDING_ACCEPT', 'REASSIGNING')")
    int cancelForBooking(@Param("bookingId") Long bookingId);

    /**
     * 更新当前分配与最近分配时间（创建任务和改派后调用）。
     *
     * @param jobId           任务 ID
     * @param assignmentId    当前分配记录 ID
     * @param lastAssignedAt  最近分配时间
     * @param expectedStatus  期望的当前状态
     * @return 影响行数
     */
    @Update("UPDATE dispatch_job SET current_assignment_id = #{assignmentId}, "
            + "last_assigned_at = #{lastAssignedAt}, version = version + 1 "
            + "WHERE id = #{jobId} AND status = #{expectedStatus}")
    int markCurrentAssignment(@Param("jobId") Long jobId,
                              @Param("assignmentId") Long assignmentId,
                              @Param("lastAssignedAt") LocalDateTime lastAssignedAt,
                              @Param("expectedStatus") String expectedStatus);

    /**
     * 按订单查询派单任务。
     *
     * @param bookingId 订单 ID
     * @return 任务或 null
     */
    @Select("SELECT * FROM dispatch_job WHERE booking_id = #{bookingId}")
    DispatchJobEntity selectByBookingId(@Param("bookingId") Long bookingId);
}
