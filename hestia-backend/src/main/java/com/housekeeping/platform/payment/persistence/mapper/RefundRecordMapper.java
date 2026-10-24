package com.housekeeping.platform.payment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.payment.persistence.entity.RefundRecordEntity;
import com.housekeeping.platform.payment.persistence.query.RefundDailyStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 退款记录 Mapper。
 *
 * <p>审批与执行分开：approve/reject 只作用于 REQUESTED，
 * 执行链路 PROCESSING → SUCCESS/FAILED 全部条件更新；
 * 退款总额不超过支付金额的校验在订单侧 {@code applyRefund} 的 WHERE 里兜底。</p>
 */
@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecordEntity> {

    /**
     * 审批通过：REQUESTED → APPROVED。
     *
     * @param refundNo           退款单号
     * @param reviewedByAccountId 审核人账号 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE refund_record SET status = 'APPROVED', "
            + "reviewed_by_account_id = #{reviewedByAccountId}, "
            + "reviewed_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status = 'REQUESTED'")
    int approve(@Param("refundNo") String refundNo,
                @Param("reviewedByAccountId") Long reviewedByAccountId);

    /**
     * 审批拒绝：REQUESTED → REJECTED。
     *
     * @param refundNo            退款单号
     * @param reviewedByAccountId 审核人账号 ID
     * @param reasonText          拒绝原因
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE refund_record SET status = 'REJECTED', "
            + "reviewed_by_account_id = #{reviewedByAccountId}, "
            + "reviewed_at = CURRENT_TIMESTAMP(3), failure_reason = #{reasonText}, "
            + "version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status = 'REQUESTED'")
    int reject(@Param("refundNo") String refundNo,
               @Param("reviewedByAccountId") Long reviewedByAccountId,
               @Param("reasonText") String reasonText);

    /**
     * 开始执行：APPROVED/FAILED → PROCESSING（FAILED 重试共用）。
     *
     * @param refundNo 退款单号
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE refund_record SET status = 'PROCESSING', failure_reason = NULL, "
            + "version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status IN ('APPROVED', 'FAILED')")
    int markProcessing(@Param("refundNo") String refundNo);

    /**
     * 执行成功：PROCESSING → SUCCESS。
     *
     * @param refundNo  退款单号
     * @param refundedAt 退款完成时间
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE refund_record SET status = 'SUCCESS', refunded_at = #{refundedAt}, "
            + "version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status = 'PROCESSING'")
    int markSuccess(@Param("refundNo") String refundNo,
                    @Param("refundedAt") LocalDateTime refundedAt);

    /**
     * 执行失败：PROCESSING → FAILED（保存失败原因，允许重试）。
     *
     * @param refundNo      退款单号
     * @param failureReason 失败原因
     * @return 影响行数
     */
    @Update("UPDATE refund_record SET status = 'FAILED', failure_reason = #{failureReason}, "
            + "version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status = 'PROCESSING'")
    int markFailed(@Param("refundNo") String refundNo,
                   @Param("failureReason") String failureReason);

    /**
     * 按退款单号查询。
     *
     * @param refundNo 退款单号
     * @return 退款记录或 null
     */
    @Select("SELECT * FROM refund_record WHERE refund_no = #{refundNo}")
    RefundRecordEntity selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 同一支付记录下的幂等键查询（uk_refund_record_idempotency 命中时回放既有申请）。
     *
     * @param paymentId      支付记录 ID
     * @param idempotencyKey 幂等键
     * @return 退款记录或 null
     */
    @Select("SELECT * FROM refund_record WHERE payment_id = #{paymentId} "
            + "AND idempotency_key = #{idempotencyKey}")
    RefundRecordEntity selectByIdempotencyKey(@Param("paymentId") Long paymentId,
                                              @Param("idempotencyKey") String idempotencyKey);

    /**
     * 订单全部退款记录（详情页流水）。
     *
     * @param bookingId 订单 ID
     * @return 退款记录列表（时间升序）
     */
    @Select("SELECT * FROM refund_record WHERE booking_id = #{bookingId} ORDER BY id ASC")
    List<RefundRecordEntity> selectByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 同一支付记录下已成功退款总额（创建申请时的上限校验）。
     *
     * @param paymentId 支付记录 ID
     * @return 已成功退款总额，无记录返回 0
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM refund_record "
            + "WHERE payment_id = #{paymentId} AND status = 'SUCCESS'")
    BigDecimal sumSuccessAmount(@Param("paymentId") Long paymentId);

    /**
     * 分页查询退款记录（运营视角）。
     *
     * @param status 状态过滤，可空
     * @param offset 偏移
     * @param limit  条数
     * @return 退款记录列表（时间倒序）
     */
    @Select("<script>SELECT * FROM refund_record "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY id DESC LIMIT #{offset}, #{limit}</script>")
    List<RefundRecordEntity> selectPage(@Param("status") String status,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);

    /**
     * 统计退款记录总数（运营分页用）。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Select("<script>SELECT COUNT(*) FROM refund_record "
            + "<where><if test='status != null'>status = #{status}</if></where></script>")
    long count(@Param("status") String status);

    // ------------------------------------------------------------------
    // 运营统计契约（docs/modules/13 第 7.1 节，只读聚合）
    // ------------------------------------------------------------------

    /**
     * 按退款完成日 + 区域分组统计退款金额（日报读模型用）。
     *
     * <p>区域归属通过联订单表获得（只读 booking_order，不修改）；联表是
     * 报表聚合的标准做法，不违反"不修改其他模块业务表"的边界。</p>
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 分组金额统计行
     */
    @Select("SELECT DATE(r.refunded_at) AS statDate, bo.service_area_id AS serviceAreaId, "
            + "COUNT(*) AS cnt, SUM(r.amount) AS amount "
            + "FROM refund_record r "
            + "JOIN booking_order bo ON bo.id = r.booking_id "
            + "WHERE r.status = 'SUCCESS' AND r.refunded_at >= #{from} "
            + "AND r.refunded_at < DATE_ADD(#{to}, INTERVAL 1 DAY) "
            + "GROUP BY statDate, serviceAreaId")
    List<RefundDailyStatRow> selectRefundDailyStats(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /**
     * 待处理退款单数（运营工作台用）：REQUESTED / APPROVED / PROCESSING。
     *
     * @param areaIds 区域过滤；null 或空 = 不限区域
     * @return 待处理退款单数
     */
    @Select("<script>SELECT COUNT(*) FROM refund_record r "
            + "JOIN booking_order bo ON bo.id = r.booking_id "
            + "WHERE r.status IN ('REQUESTED', 'APPROVED', 'PROCESSING') "
            + "<if test='areaIds != null and areaIds.size() > 0'>"
            + "AND bo.service_area_id IN "
            + "<foreach item='id' collection='areaIds' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</if></script>")
    long countPending(@Param("areaIds") Collection<Long> areaIds);
}
