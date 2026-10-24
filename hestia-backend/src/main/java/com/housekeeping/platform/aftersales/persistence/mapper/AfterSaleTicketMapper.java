package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleTicketEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 售后工单 Mapper。
 *
 * <p>状态流转一律条件更新（docs/modules/11 第 6.3 节），影响行数 0 即冲突。
 * 工单只写结论字段，不触碰订单与支付表。</p>
 */
@Mapper
public interface AfterSaleTicketMapper extends BaseMapper<AfterSaleTicketEntity> {

    /**
     * 按工单号查询。
     *
     * @param ticketNo 工单号
     * @return 工单或 null
     */
    @Select("SELECT * FROM after_sale_ticket WHERE ticket_no = #{ticketNo}")
    AfterSaleTicketEntity selectByTicketNo(@Param("ticketNo") String ticketNo);

    /**
     * 受理：SUBMITTED → PROCESSING（记录受理运营）。
     *
     * @param ticketNo  工单号
     * @param handlerId 受理运营账号 ID
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = 'PROCESSING', handler_id = #{handlerId}, "
            + "version = version + 1 WHERE ticket_no = #{ticketNo} AND status = 'SUBMITTED'")
    int accept(@Param("ticketNo") String ticketNo, @Param("handlerId") long handlerId);

    /**
     * 转挂起：PROCESSING → PENDING_*（等待客户/人员/复核）。
     *
     * @param ticketNo 工单号
     * @param toStatus 目标挂起状态
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = #{toStatus}, version = version + 1 "
            + "WHERE ticket_no = #{ticketNo} AND status = 'PROCESSING'")
    int toPending(@Param("ticketNo") String ticketNo,
                  @Param("toStatus") String toStatus);

    /**
     * 回到处理中：PENDING_* → PROCESSING。
     *
     * @param ticketNo 工单号
     * @param fromStatus 当前挂起状态
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = 'PROCESSING', version = version + 1 "
            + "WHERE ticket_no = #{ticketNo} AND status = #{fromStatus}")
    int backToProcessing(@Param("ticketNo") String ticketNo,
                         @Param("fromStatus") String fromStatus);

    /**
     * 形成结论：活动状态 → RESOLVED（必须写结论文本，CHECK 约束兜底）。
     *
     * @param ticketNo        工单号
     * @param resolutionType  处理结果
     * @param resolutionText  结论说明
     * @param responsibility  责任认定
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = 'RESOLVED', resolution_type = #{resolutionType}, "
            + "resolution_text = #{resolutionText}, responsibility = #{responsibility}, "
            + "resolved_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE ticket_no = #{ticketNo} "
            + "AND status IN ('PROCESSING', 'PENDING_CUSTOMER', 'PENDING_WORKER', 'PENDING_REVIEW')")
    int resolve(@Param("ticketNo") String ticketNo,
                @Param("resolutionType") String resolutionType,
                @Param("resolutionText") String resolutionText,
                @Param("responsibility") String responsibility);

    /**
     * 驳回：活动状态 → REJECTED（投诉不成立）。
     *
     * @param ticketNo 工单号
     * @param reason   驳回理由
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = 'REJECTED', resolution_text = #{reason}, "
            + "resolved_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE ticket_no = #{ticketNo} "
            + "AND status IN ('SUBMITTED', 'PROCESSING', 'PENDING_CUSTOMER', "
            + "'PENDING_WORKER', 'PENDING_REVIEW')")
    int reject(@Param("ticketNo") String ticketNo, @Param("reason") String reason);

    /**
     * 关闭：RESOLVED / REJECTED → CLOSED（终态）。
     *
     * @param ticketNo 工单号
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE ticket_no = #{ticketNo} "
            + "AND status IN ('RESOLVED', 'REJECTED')")
    int close(@Param("ticketNo") String ticketNo);

    /**
     * 客户确认处理方案：PENDING_CUSTOMER → CLOSED（结论缺失时补默认文案，
     * 满足 CHECK 约束"终态必须有结论文本"）。
     *
     * @param ticketNo 工单号
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3), "
            + "resolution_text = COALESCE(resolution_text, '客户确认处理方案'), "
            + "version = version + 1 WHERE ticket_no = #{ticketNo} AND status = 'PENDING_CUSTOMER'")
    int acceptByCustomer(@Param("ticketNo") String ticketNo);

    /**
     * 绑定退款记录 ID（退款申请创建成功后回填引用）。
     *
     * @param ticketNo 工单号
     * @param refundId 退款记录 ID
     * @return 影响行数
     */
    @Update("UPDATE after_sale_ticket SET refund_id = #{refundId}, version = version + 1 "
            + "WHERE ticket_no = #{ticketNo} AND refund_id IS NULL")
    int linkRefund(@Param("ticketNo") String ticketNo, @Param("refundId") long refundId);

    /**
     * 分页查询工单（运营视角，可按状态/类型过滤）。
     *
     * @param status     状态（可空）
     * @param ticketType 类型（可空）
     * @param offset     偏移
     * @param size       条数
     * @return 工单列表
     */
    @Select("<script>SELECT * FROM after_sale_ticket "
            + "<where>"
            + "<if test='status != null'>AND status = #{status}</if>"
            + "<if test='ticketType != null'>AND ticket_type = #{ticketType}</if>"
            + "</where> ORDER BY submitted_at DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<AfterSaleTicketEntity> selectPage(@Param("status") String status,
                                           @Param("ticketType") String ticketType,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    /**
     * 统计工单总数（运营视角）。
     *
     * @param status     状态（可空）
     * @param ticketType 类型（可空）
     * @return 总数
     */
    @Select("<script>SELECT COUNT(*) FROM after_sale_ticket "
            + "<where>"
            + "<if test='status != null'>AND status = #{status}</if>"
            + "<if test='ticketType != null'>AND ticket_type = #{ticketType}</if>"
            + "</where></script>")
    long count(@Param("status") String status, @Param("ticketType") String ticketType);

    /**
     * 分页查询客户的工单。
     *
     * @param customerId 客户 ID
     * @param offset     偏移
     * @param size       条数
     * @return 工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE customer_id = #{customerId} "
            + "ORDER BY submitted_at DESC LIMIT #{size} OFFSET #{offset}")
    List<AfterSaleTicketEntity> selectPageByCustomer(@Param("customerId") long customerId,
                                                     @Param("offset") int offset,
                                                     @Param("size") int size);

    /**
     * 统计客户的工单总数。
     *
     * @param customerId 客户 ID
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM after_sale_ticket WHERE customer_id = #{customerId}")
    long countByCustomer(@Param("customerId") long customerId);

    /**
     * 分页查询人员的工单。
     *
     * @param workerId 人员 ID
     * @param offset   偏移
     * @param size     条数
     * @return 工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE worker_id = #{workerId} "
            + "ORDER BY submitted_at DESC LIMIT #{size} OFFSET #{offset}")
    List<AfterSaleTicketEntity> selectPageByWorker(@Param("workerId") long workerId,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /**
     * 统计人员的工单总数。
     *
     * @param workerId 人员 ID
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM after_sale_ticket WHERE worker_id = #{workerId}")
    long countByWorker(@Param("workerId") long workerId);

    /**
     * 已超处理时限且仍未终态的工单（SLA 扫描用）。
     *
     * @param asOf  时间基准
     * @param limit 扫描条数上限
     * @return 超时工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE due_at IS NOT NULL AND due_at &lt; #{asOf} "
            + "AND status NOT IN ('RESOLVED', 'REJECTED', 'CLOSED') LIMIT #{limit}")
    List<AfterSaleTicketEntity> selectOverdue(@Param("asOf") java.time.LocalDateTime asOf,
                                              @Param("limit") int limit);

    /**
     * 人员的全部未驳回工单（质量汇总重算用：被驳回的投诉不计入统计）。
     *
     * @param workerId 人员 ID
     * @return 工单列表（仅含 id 与 ticket_type 即可）
     */
    @Select("SELECT id, ticket_type FROM after_sale_ticket "
            + "WHERE worker_id = #{workerId} AND status != 'REJECTED'")
    List<AfterSaleTicketEntity> selectNonRejectedByWorker(@Param("workerId") long workerId);

    // ------------------------------------------------------------------
    // 运营统计契约（docs/modules/13 第 7.3 节，只读聚合）
    // ------------------------------------------------------------------

    /**
     * 按工单提交日 + 区域分组统计投诉 / 售后工单数（日报读模型用）。
     *
     * <p>区域归属通过联订单表获得（只读 booking_order，不修改）；
     * 被驳回的工单不计入投诉口径。</p>
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return statDate → serviceAreaId / cnt 行
     */
    @Select("SELECT DATE(t.submitted_at) AS statDate, bo.service_area_id AS serviceAreaId, "
            + "COUNT(*) AS cnt "
            + "FROM after_sale_ticket t "
            + "JOIN booking_order bo ON bo.id = t.booking_id "
            + "WHERE t.status != 'REJECTED' AND t.submitted_at >= #{from} "
            + "AND t.submitted_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate, serviceAreaId")
    List<Map<String, Object>> selectComplaintDaily(@Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /**
     * 按工单提交日 + 区域分组统计返工工单数（存在返工订单引用的工单）。
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return statDate → serviceAreaId / cnt 行
     */
    @Select("SELECT DATE(t.submitted_at) AS statDate, bo.service_area_id AS serviceAreaId, "
            + "COUNT(*) AS cnt "
            + "FROM after_sale_ticket t "
            + "JOIN booking_order bo ON bo.id = t.booking_id "
            + "WHERE t.rework_booking_id IS NOT NULL AND t.submitted_at >= #{from} "
            + "AND t.submitted_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate, serviceAreaId")
    List<Map<String, Object>> selectReworkDaily(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /**
     * 未结工单数（运营工作台"待处理投诉"）。
     *
     * @param areaIds 区域过滤；null 或空 = 不限区域
     * @return 未结工单数
     */
    @Select("<script>SELECT COUNT(*) FROM after_sale_ticket t "
            + "JOIN booking_order bo ON bo.id = t.booking_id "
            + "WHERE t.status IN ('SUBMITTED', 'PROCESSING', 'PENDING_CUSTOMER', "
            + "'PENDING_WORKER', 'PENDING_REVIEW') "
            + "<if test='areaIds != null and areaIds.size() > 0'>"
            + "AND bo.service_area_id IN "
            + "<foreach item='id' collection='areaIds' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</if></script>")
    long countOpen(@Param("areaIds") Collection<Long> areaIds);
}
