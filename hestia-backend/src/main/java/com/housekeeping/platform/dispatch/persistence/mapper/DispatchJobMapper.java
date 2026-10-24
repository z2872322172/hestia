package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 按任务单号查询。
     *
     * @param jobNo 任务单号
     * @return 任务或 null
     */
    @Select("SELECT * FROM dispatch_job WHERE job_no = #{jobNo}")
    DispatchJobEntity selectByJobNo(@Param("jobNo") String jobNo);

    /**
     * 人员接单：PENDING_ACCEPT → ACCEPTED（docs/modules/08 第 8.3 节）。
     *
     * @param jobId 任务 ID
     * @return 影响行数，0 表示状态已变（已进入改派/取消）
     */
    @Update("UPDATE dispatch_job SET status = 'ACCEPTED', version = version + 1 "
            + "WHERE id = #{jobId} AND status = 'PENDING_ACCEPT'")
    int markAccepted(@Param("jobId") Long jobId);

    /**
     * 进入改派：PENDING_ACCEPT → REASSIGNING（拒单/超时后触发，第 8.4 节）。
     *
     * @param jobId 任务 ID
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE dispatch_job SET status = 'REASSIGNING', version = version + 1 "
            + "WHERE id = #{jobId} AND status = 'PENDING_ACCEPT'")
    int markReassigningFromPending(@Param("jobId") Long jobId);

    /**
     * 手工改派已接单任务：ACCEPTED → REASSIGNING（docs/modules/08 第 9 节）。
     *
     * @param jobId 任务 ID
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE dispatch_job SET status = 'REASSIGNING', version = version + 1 "
            + "WHERE id = #{jobId} AND status = 'ACCEPTED'")
    int markReassigningFromAccepted(@Param("jobId") Long jobId);

    /**
     * 改派落到新候选：REASSIGNING → PENDING_ACCEPT，更新当前分配与尝试次数。
     *
     * <p>条件更新同时是自动改派的并发防护：两条链路同时改派时只有一条成功，
     * 失败方影响行数 0 抛异常整体回滚。</p>
     *
     * @param jobId          任务 ID
     * @param assignmentId   新分配记录 ID
     * @param lastAssignedAt 最近分配时间
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE dispatch_job SET status = 'PENDING_ACCEPT', "
            + "current_assignment_id = #{assignmentId}, last_assigned_at = #{lastAssignedAt}, "
            + "attempt_count = attempt_count + 1, version = version + 1 "
            + "WHERE id = #{jobId} AND status = 'REASSIGNING'")
    int markPendingAccept(@Param("jobId") Long jobId,
                          @Param("assignmentId") Long assignmentId,
                          @Param("lastAssignedAt") LocalDateTime lastAssignedAt);

    /**
     * 手工派单进入执行：MANUAL_REQUIRED → REASSIGNING，
     * 随后由 {@link #markPendingAccept} 落到指定人员（统一改派路径）。
     *
     * @param jobId 任务 ID
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE dispatch_job SET status = 'REASSIGNING', version = version + 1 "
            + "WHERE id = #{jobId} AND status = 'MANUAL_REQUIRED'")
    int markReassigningFromManual(@Param("jobId") Long jobId);

    /**
     * 无候选可派：REASSIGNING → MANUAL_REQUIRED 并记录原因（第 8.4 节）。
     *
     * @param jobId  任务 ID
     * @param reason 原因（可空）
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE dispatch_job SET status = 'MANUAL_REQUIRED', "
            + "manual_required_reason = #{reason}, version = version + 1 "
            + "WHERE id = #{jobId} AND status = 'REASSIGNING'")
    int markManualRequired(@Param("jobId") Long jobId, @Param("reason") String reason);

    /**
     * 运营任务队列分页（状态可空）。
     *
     * @param status 状态过滤，可空
     * @param offset 偏移
     * @param size   每页大小
     * @return 任务列表（更新时间倒序）
     */
    @Select("<script>SELECT * FROM dispatch_job "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY updated_at DESC, id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<DispatchJobEntity> selectPage(@Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /**
     * 运营任务队列总数（状态可空）。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Select("<script>SELECT COUNT(*) FROM dispatch_job "
            + "<where><if test='status != null'>status = #{status}</if></where></script>")
    long count(@Param("status") String status);
}
