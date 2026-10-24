package com.housekeeping.platform.fulfillment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 履约记录 Mapper。
 *
 * <p>状态流转一律条件更新（WHERE 状态 + 当前人员），影响行数 0 即冲突：
 * 人员并发操作、客户确认与自动确认同时发生时只有一个成功。</p>
 */
@Mapper
public interface ServiceRecordMapper extends BaseMapper<ServiceRecordEntity> {

    /**
     * 按订单查询履约记录（一单一记录）。
     *
     * @param bookingId 订单 ID
     * @return 记录或 null
     */
    @Select("SELECT * FROM service_record WHERE booking_id = #{bookingId}")
    ServiceRecordEntity selectByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 按记录单号查询。
     *
     * @param recordNo 记录单号
     * @return 记录或 null
     */
    @Select("SELECT * FROM service_record WHERE record_no = #{recordNo}")
    ServiceRecordEntity selectByRecordNo(@Param("recordNo") String recordNo);

    /**
     * 出发：PENDING → EN_ROUTE（仅当前人员）。
     *
     * @param recordId 记录 ID
     * @param workerId 人员 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'EN_ROUTE', departed_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{recordId} AND worker_id = #{workerId} "
            + "AND status = 'PENDING'")
    int depart(@Param("recordId") Long recordId, @Param("workerId") Long workerId);

    /**
     * 签到：EN_ROUTE → CHECKED_IN（仅当前人员）。
     *
     * @param recordId  记录 ID
     * @param workerId  人员 ID
     * @param longitude 经度（可空）
     * @param latitude  纬度（可空）
     * @param source    签到来源 GPS / MANUAL
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'CHECKED_IN', "
            + "check_in_at = CURRENT_TIMESTAMP(3), check_in_longitude = #{longitude}, "
            + "check_in_latitude = #{latitude}, check_in_source = #{source}, "
            + "version = version + 1 WHERE id = #{recordId} AND worker_id = #{workerId} "
            + "AND status = 'EN_ROUTE'")
    int checkIn(@Param("recordId") Long recordId, @Param("workerId") Long workerId,
                @Param("longitude") BigDecimal longitude,
                @Param("latitude") BigDecimal latitude,
                @Param("source") String source);

    /**
     * 开始服务：CHECKED_IN → IN_SERVICE（仅当前人员）。
     *
     * @param recordId 记录 ID
     * @param workerId 人员 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'IN_SERVICE', started_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{recordId} AND worker_id = #{workerId} "
            + "AND status = 'CHECKED_IN'")
    int start(@Param("recordId") Long recordId, @Param("workerId") Long workerId);

    /**
     * 申请完成：IN_SERVICE → PENDING_CONFIRM 并设置自动确认截止时间（仅当前人员）。
     *
     * @param recordId  记录 ID
     * @param workerId  人员 ID
     * @param deadline  自动确认截止时间
     * @param result    服务结果说明（可空）
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'PENDING_CONFIRM', "
            + "finished_at = CURRENT_TIMESTAMP(3), completion_requested_at = CURRENT_TIMESTAMP(3), "
            + "confirmation_deadline = #{deadline}, service_result = #{result}, "
            + "version = version + 1 WHERE id = #{recordId} AND worker_id = #{workerId} "
            + "AND status = 'IN_SERVICE'")
    int requestComplete(@Param("recordId") Long recordId, @Param("workerId") Long workerId,
                        @Param("deadline") LocalDateTime deadline,
                        @Param("result") String result);

    /**
     * 客户确认完成：PENDING_CONFIRM → COMPLETED（docs/modules/09 第 11.2 节）。
     *
     * <p>与自动确认并发时条件更新只有一个成功。</p>
     *
     * @param recordId 记录 ID
     * @param source   CUSTOMER / SYSTEM / OPERATOR
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'COMPLETED', "
            + "customer_confirmed_at = CURRENT_TIMESTAMP(3), confirm_source = #{source}, "
            + "version = version + 1 WHERE id = #{recordId} AND status = 'PENDING_CONFIRM'")
    int complete(@Param("recordId") Long recordId, @Param("source") String source);

    /**
     * 运营强制完成：IN_SERVICE / PENDING_CONFIRM / EXCEPTION → COMPLETED。
     *
     * @param recordId 记录 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'COMPLETED', "
            + "customer_confirmed_at = CURRENT_TIMESTAMP(3), confirm_source = 'OPERATOR', "
            + "version = version + 1 WHERE id = #{recordId} AND status IN "
            + "('IN_SERVICE', 'PENDING_CONFIRM', 'EXCEPTION')")
    int forceComplete(@Param("recordId") Long recordId);

    /**
     * 上报异常转 EXCEPTION（EN_ROUTE / CHECKED_IN / IN_SERVICE → EXCEPTION）。
     *
     * @param recordId 记录 ID
     * @param workerId 人员 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'EXCEPTION', version = version + 1 "
            + "WHERE id = #{recordId} AND worker_id = #{workerId} AND status IN "
            + "('EN_ROUTE', 'CHECKED_IN', 'IN_SERVICE')")
    int markException(@Param("recordId") Long recordId, @Param("workerId") Long workerId);

    /**
     * 异常处理完成恢复服务：EXCEPTION → IN_SERVICE（docs/modules/09 第 5.2 节）。
     *
     * @param recordId 记录 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'IN_SERVICE', version = version + 1 "
            + "WHERE id = #{recordId} AND status = 'EXCEPTION'")
    int resumeFromException(@Param("recordId") Long recordId);

    /**
     * 取消履约（订单取消/改期联动）：进行中状态 → CANCELED。
     *
     * @param bookingId 订单 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET status = 'CANCELED', version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status IN "
            + "('PENDING', 'EN_ROUTE', 'CHECKED_IN', 'IN_SERVICE', 'EXCEPTION')")
    int cancelForBooking(@Param("bookingId") Long bookingId);

    /**
     * 改派重置（docs/modules/09 第 18.2 节）：未完成记录换绑新人员回到 PENDING，
     * 执行字段清空（已 COMPLETED / CANCELED 的记录不动）。
     *
     * @param bookingId  订单 ID
     * @param newWorkerId 新人员 ID
     * @return 影响行数
     */
    @Update("UPDATE service_record SET worker_id = #{newWorkerId}, status = 'PENDING', "
            + "departed_at = NULL, check_in_at = NULL, check_in_longitude = NULL, "
            + "check_in_latitude = NULL, check_in_source = NULL, started_at = NULL, "
            + "finished_at = NULL, completion_requested_at = NULL, confirmation_deadline = NULL, "
            + "customer_confirmed_at = NULL, confirm_source = NULL, version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status NOT IN ('COMPLETED', 'CANCELED')")
    int resetForReassignment(@Param("bookingId") Long bookingId,
                             @Param("newWorkerId") Long newWorkerId);

    /**
     * 到期待确认记录（自动确认任务扫描）。
     *
     * @param asOf  视作当前时刻
     * @param limit 单批上限
     * @return 记录列表
     */
    @Select("SELECT * FROM service_record WHERE status = 'PENDING_CONFIRM' "
            + "AND confirmation_deadline IS NOT NULL AND confirmation_deadline < #{asOf} "
            + "ORDER BY confirmation_deadline ASC LIMIT #{limit}")
    List<ServiceRecordEntity> selectDueConfirmations(@Param("asOf") LocalDateTime asOf,
                                                     @Param("limit") int limit);

    /**
     * 运营履约列表分页（状态可空）。
     *
     * @param status 状态过滤，可空
     * @param offset 偏移
     * @param size   每页大小
     * @return 记录列表（更新时间倒序）
     */
    @Select("<script>SELECT * FROM service_record "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY updated_at DESC, id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ServiceRecordEntity> selectPage(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /**
     * 运营履约列表总数（状态可空）。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Select("<script>SELECT COUNT(*) FROM service_record "
            + "<where><if test='status != null'>status = #{status}</if></where></script>")
    long count(@Param("status") String status);

    /**
     * 按状态统计履约记录数（运营监控/看板用，docs/modules/13 第 7.3 节）。
     *
     * <p>区域归属经 {@code booking_order} 只读联表获得（不修改订单表）；
     * areaIds 为空 = 不限区域（ADMIN）。</p>
     *
     * @param areaIds 授权服务区域 ID 集合（可空 = 不限）
     * @param status  履约状态
     * @return 记录数
     */
    @Select("<script>SELECT COUNT(*) FROM service_record sr "
            + "JOIN booking_order bo ON bo.id = sr.booking_id "
            + "WHERE sr.status = #{status} "
            + "<if test='areaIds != null and areaIds.size() > 0'>"
            + "AND bo.service_area_id IN "
            + "<foreach collection='areaIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</if></script>")
    long countByStatusInAreas(@Param("areaIds") Collection<Long> areaIds,
                              @Param("status") String status);
}
