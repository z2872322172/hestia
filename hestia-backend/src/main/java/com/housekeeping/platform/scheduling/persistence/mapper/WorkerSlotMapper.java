package com.housekeeping.platform.scheduling.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerSlotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 人员时段库存 Mapper。
 *
 * <p><b>所有状态变更只允许走本接口的条件更新</b>：WHERE 里带当前状态与（锁时段时的）
 * 乐观锁版本，影响行数为 0 即表示并发竞争失败。两个订单抢同一时段只有一个成功；
 * 释放重复执行也不会命中已变化的状态——这是时段不超卖的唯一保障。</p>
 */
@Mapper
public interface WorkerSlotMapper extends BaseMapper<WorkerSlotEntity> {

    /**
     * 锁定时段：AVAILABLE → HELD（docs/modules/06 第 9 节）。
     *
     * <p>必须带 {@code expectedVersion}：订单事务内先读后锁，版本不匹配说明
     * 读到锁之间时段被人动了，宁可让订单重建也不能超卖。</p>
     *
     * @param slotId         时段 ID
     * @param bookingId      订单 ID
     * @param holdExpiresAt  锁定到期时间
     * @param expectedVersion 读取时的版本号
     * @return 影响行数，1 成功 / 0 失败
     */
    @Update("UPDATE worker_slot SET status = 'HELD', booking_id = #{bookingId}, "
            + "hold_expires_at = #{holdExpiresAt}, version = version + 1 "
            + "WHERE id = #{slotId} AND status = 'AVAILABLE' AND version = #{expectedVersion}")
    int hold(@Param("slotId") Long slotId,
             @Param("bookingId") Long bookingId,
             @Param("holdExpiresAt") java.time.LocalDateTime holdExpiresAt,
             @Param("expectedVersion") long expectedVersion);

    /**
     * 支付成功确认：HELD → BOOKED（docs/modules/06 第 10 节）。
     *
     * <p>必须带 {@code bookingId}：防止把别的订单锁的时段确认掉。到期时间清空，
     * 与数据库约束 {@code chk_worker_slot_order_binding} 保持一致。</p>
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 失败
     */
    @Update("UPDATE worker_slot SET status = 'BOOKED', hold_expires_at = NULL, "
            + "version = version + 1 WHERE id = #{slotId} AND booking_id = #{bookingId} "
            + "AND status = 'HELD'")
    int confirm(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 释放待支付锁定：HELD → AVAILABLE（支付超时或未支付取消）。
     *
     * <p>带 {@code bookingId} 条件：订单已支付或时段被重新占用时，
     * 重复释放不产生任何效果。</p>
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 失败
     */
    @Update("UPDATE worker_slot SET status = 'AVAILABLE', booking_id = NULL, "
            + "hold_expires_at = NULL, version = version + 1 "
            + "WHERE id = #{slotId} AND booking_id = #{bookingId} AND status = 'HELD'")
    int releaseHeld(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 释放已过期待支付的锁定（定时任务用，docs/modules/06 第 10 节）。
     *
     * <p>与 {@link #releaseHeld} 的区别是多了到期时间条件：批量任务只清理
     * 确实过期的锁定，未到期的不能碰。</p>
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 失败
     */
    @Update("UPDATE worker_slot SET status = 'AVAILABLE', booking_id = NULL, "
            + "hold_expires_at = NULL, version = version + 1 "
            + "WHERE id = #{slotId} AND booking_id = #{bookingId} AND status = 'HELD' "
            + "AND hold_expires_at <= CURRENT_TIMESTAMP(3)")
    int releaseExpiredHeld(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 释放已支付订单占用：BOOKED → AVAILABLE（取消已支付订单且日期未过）。
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 失败
     */
    @Update("UPDATE worker_slot SET status = 'AVAILABLE', booking_id = NULL, "
            + "version = version + 1 "
            + "WHERE id = #{slotId} AND booking_id = #{bookingId} AND status = 'BOOKED'")
    int releaseBooked(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 关闭已支付订单占用：BOOKED → CLOSED（取消订单时日期已过或排班已失效）。
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 失败
     */
    @Update("UPDATE worker_slot SET status = 'CLOSED', version = version + 1 "
            + "WHERE id = #{slotId} AND booking_id = #{bookingId} AND status = 'BOOKED'")
    int closeBooked(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 阻塞空闲时段（请假、停排、排班取消时使用）。
     *
     * <p>只允许 AVAILABLE → BLOCKED：HELD/BOOKED 必须走改派流程，
     * 不能被排班变更静默吞掉（docs/modules/06 第 12.2 节）。</p>
     *
     * @param slotId 时段 ID
     * @return 影响行数，1 成功 / 0 时段已占用
     */
    @Update("UPDATE worker_slot SET status = 'BLOCKED', version = version + 1 "
            + "WHERE id = #{slotId} AND status = 'AVAILABLE'")
    int blockAvailable(@Param("slotId") Long slotId);

    /**
     * 解除阻塞：BLOCKED → AVAILABLE（请假撤销、停排恢复）。
     *
     * @param slotId 时段 ID
     * @return 影响行数
     */
    @Update("UPDATE worker_slot SET status = 'AVAILABLE', version = version + 1 "
            + "WHERE id = #{slotId} AND status = 'BLOCKED'")
    int unblock(@Param("slotId") Long slotId);

    /**
     * 关闭空闲时段（排班取消且日期已过等场景）。
     *
     * @param slotId 时段 ID
     * @return 影响行数
     */
    @Update("UPDATE worker_slot SET status = 'CLOSED', version = version + 1 "
            + "WHERE id = #{slotId} AND status = 'AVAILABLE'")
    int closeAvailable(@Param("slotId") Long slotId);

    /**
     * 将历史日期的空闲时段标记为关闭（定时任务，docs/modules/06 第 4.4 节）。
     *
     * @param beforeDate 该日期之前的 AVAILABLE 时段全部关闭
     * @return 影响行数
     */
    @Update("UPDATE worker_slot SET status = 'CLOSED', version = version + 1 "
            + "WHERE status = 'AVAILABLE' AND service_date < #{beforeDate}")
    int closeAvailableBefore(@Param("beforeDate") LocalDate beforeDate);

    /**
     * 按订单号查时段（订单取消/改期时定位要释放的时段）。
     *
     * @param bookingId 订单 ID
     * @return 该订单占用的时段（同一时刻最多一个有效占用）
     */
    @Select("SELECT * FROM worker_slot WHERE booking_id = #{bookingId} "
            + "AND status IN ('HELD', 'BOOKED') LIMIT 1")
    WorkerSlotEntity selectByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 查询某日某时间窗下时段空闲的人员 ID（可用性聚合第一步）。
     *
     * <p>技能、资质、区域等人员侧过滤不在这里做——那些规则属于 workforce 模块，
     * 本查询只回答"库存里谁还有空位"。</p>
     *
     * @param serviceDate 服务日期
     * @param windowId    时间窗 ID
     * @return 人员 ID 列表
     */
    @Select("SELECT DISTINCT worker_id FROM worker_slot "
            + "WHERE service_date = #{serviceDate} AND window_id = #{windowId} "
            + "AND status = 'AVAILABLE'")
    List<Long> selectAvailableWorkerIds(@Param("serviceDate") LocalDate serviceDate,
                                        @Param("windowId") Long windowId);

    /**
     * 统计日期内各人员已支付占用的时段数（每日接单上限过滤）。
     *
     * <p>一条订单占用一个完整时间窗，BOOKED 数即当日有效订单数。</p>
     *
     * @param serviceDate 服务日期
     * @return worker_id → booked 数
     */
    @Select("SELECT worker_id AS workerId, COUNT(*) AS bookedCount FROM worker_slot "
            + "WHERE service_date = #{serviceDate} AND status = 'BOOKED' GROUP BY worker_id")
    List<Map<String, Object>> countBookedGroupByWorker(@Param("serviceDate") LocalDate serviceDate);

    /**
     * 查询人员在日期区间内的时段（本人排班视图）。
     *
     * @param workerId 人员 ID
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 时段列表
     */
    @Select("SELECT * FROM worker_slot WHERE worker_id = #{workerId} "
            + "AND service_date BETWEEN #{dateFrom} AND #{dateTo} "
            + "ORDER BY service_date ASC, window_id ASC")
    List<WorkerSlotEntity> selectByWorkerAndDateRange(@Param("workerId") Long workerId,
                                                      @Param("dateFrom") LocalDate dateFrom,
                                                      @Param("dateTo") LocalDate dateTo);

    /**
     * 按服务日期统计时段总数与已支付占用数（运营统计契约，docs/modules/13 第 7.4 节）。
     *
     * <p>全公司口径：时段本身不携带区域属性。</p>
     *
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return statDate → workerSlotTotal / workerSlotBooked 两行
     */
    @Select("SELECT service_date AS statDate, COUNT(*) AS cnt, "
            + "SUM(status = 'BOOKED') AS booked FROM worker_slot "
            + "WHERE service_date BETWEEN #{dateFrom} AND #{dateTo} "
            + "GROUP BY service_date")
    List<Map<String, Object>> selectSlotDailyStats(@Param("dateFrom") LocalDate dateFrom,
                                                   @Param("dateTo") LocalDate dateTo);
}
