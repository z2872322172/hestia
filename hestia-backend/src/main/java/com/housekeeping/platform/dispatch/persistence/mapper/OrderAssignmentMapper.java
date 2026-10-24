package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.OrderAssignmentEntity;
import com.housekeeping.platform.dispatch.persistence.query.AssignmentDateCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
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
     * 手工改派：旧分配 ACCEPTED → REASSIGNED（已接单订单被运营改派）。
     *
     * @param assignmentId 旧分配记录 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE order_assignment SET status = 'REASSIGNED', version = version + 1 "
            + "WHERE id = #{assignmentId} AND status = 'ACCEPTED'")
    int reassignFromAccepted(@Param("assignmentId") Long assignmentId);

    /**
     * 人员接单：PENDING_ACCEPT → ACCEPTED 并记录接单时间。
     *
     * @param assignmentId 分配记录 ID
     * @param acceptedAt   接单时间
     * @return 影响行数，0 表示已接单/已拒/已过期
     */
    @Update("UPDATE order_assignment SET status = 'ACCEPTED', accepted_at = #{acceptedAt}, "
            + "version = version + 1 WHERE id = #{assignmentId} AND status = 'PENDING_ACCEPT'")
    int accept(@Param("assignmentId") Long assignmentId,
               @Param("acceptedAt") LocalDateTime acceptedAt);

    /**
     * 人员拒单：PENDING_ACCEPT → REJECTED 并记录原因（docs/modules/08 第 2 节原因必填）。
     *
     * @param assignmentId 分配记录 ID
     * @param rejectedAt   拒单时间
     * @param reasonCode   原因码
     * @param reasonText   原因文本
     * @return 影响行数，0 表示状态已变
     */
    @Update("UPDATE order_assignment SET status = 'REJECTED', rejected_at = #{rejectedAt}, "
            + "reject_reason_code = #{reasonCode}, reject_reason_text = #{reasonText}, "
            + "version = version + 1 WHERE id = #{assignmentId} AND status = 'PENDING_ACCEPT'")
    int reject(@Param("assignmentId") Long assignmentId,
               @Param("rejectedAt") LocalDateTime rejectedAt,
               @Param("reasonCode") String reasonCode,
               @Param("reasonText") String reasonText);

    /**
     * 接单超时失效：PENDING_ACCEPT → EXPIRED。
     *
     * @param assignmentId 分配记录 ID
     * @return 影响行数，0 表示状态已变（已被接单/拒单抢先）
     */
    @Update("UPDATE order_assignment SET status = 'EXPIRED', version = version + 1 "
            + "WHERE id = #{assignmentId} AND status = 'PENDING_ACCEPT'")
    int markExpired(@Param("assignmentId") Long assignmentId);

    /**
     * 查询接单已超时的待接单分配（超时改派任务扫描）。
     *
     * @param asOf  视作当前的时刻
     * @param limit 单批上限
     * @return 超时分配列表
     */
    @Select("SELECT * FROM order_assignment WHERE status = 'PENDING_ACCEPT' "
            + "AND accept_deadline IS NOT NULL AND accept_deadline < #{asOf} "
            + "ORDER BY accept_deadline ASC LIMIT #{limit}")
    List<OrderAssignmentEntity> selectExpired(@Param("asOf") LocalDateTime asOf,
                                              @Param("limit") int limit);

    /**
     * 按分配单号查询。
     *
     * @param assignmentNo 分配单号
     * @return 分配记录或 null
     */
    @Select("SELECT * FROM order_assignment WHERE assignment_no = #{assignmentNo}")
    OrderAssignmentEntity selectByAssignmentNo(@Param("assignmentNo") String assignmentNo);

    /**
     * 人员视角分页列表（我的派单，时间倒序）。
     *
     * @param workerId 人员 ID
     * @param offset   偏移
     * @param size     每页大小
     * @return 分配列表
     */
    @Select("SELECT * FROM order_assignment WHERE worker_id = #{workerId} "
            + "ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<OrderAssignmentEntity> selectPageByWorker(@Param("workerId") Long workerId,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /**
     * 人员视角总数。
     *
     * @param workerId 人员 ID
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM order_assignment WHERE worker_id = #{workerId}")
    long countByWorker(@Param("workerId") Long workerId);

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

    // ------------------------------------------------------------------
    // 运营统计契约（docs/modules/13 第 7.2 节，只读聚合，全公司口径）
    // ------------------------------------------------------------------

    /**
     * 自动派单接单成功数，按接单日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    @Select("SELECT DATE(accepted_at) AS statDate, COUNT(*) AS cnt "
            + "FROM order_assignment "
            + "WHERE assign_type = 'AUTO' AND status = 'ACCEPTED' AND accepted_at >= #{from} "
            + "AND accepted_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate")
    List<AssignmentDateCountRow> selectAutoSuccessDaily(@Param("from") LocalDate from,
                                                        @Param("to") LocalDate to);

    /**
     * 拒单数，按拒单日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    @Select("SELECT DATE(rejected_at) AS statDate, COUNT(*) AS cnt "
            + "FROM order_assignment "
            + "WHERE status = 'REJECTED' AND rejected_at >= #{from} "
            + "AND rejected_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate")
    List<AssignmentDateCountRow> selectRejectedDaily(@Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    /**
     * 接单超时数，按失效日分组（EXPIRED 无独立时间列，取 updated_at）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    @Select("SELECT DATE(updated_at) AS statDate, COUNT(*) AS cnt "
            + "FROM order_assignment "
            + "WHERE status = 'EXPIRED' AND updated_at >= #{from} "
            + "AND updated_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate")
    List<AssignmentDateCountRow> selectExpiredDaily(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /**
     * 人工派单数（assign_type = MANUAL），按派单日分组。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 按日期分组计数
     */
    @Select("SELECT DATE(assigned_at) AS statDate, COUNT(*) AS cnt "
            + "FROM order_assignment "
            + "WHERE assign_type = 'MANUAL' AND assigned_at >= #{from} "
            + "AND assigned_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate")
    List<AssignmentDateCountRow> selectManualDaily(@Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
