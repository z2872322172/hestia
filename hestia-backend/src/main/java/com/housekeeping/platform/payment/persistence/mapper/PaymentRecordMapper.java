package com.housekeeping.platform.payment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.payment.persistence.entity.PaymentRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付记录 Mapper。
 *
 * <p>状态流转一律条件更新（WHERE status IN 期望集合），影响行数 0 表示
 * 状态已被并发推进，由调用方决定幂等返回还是抛冲突。
 * 生成列 {@code success_booking_id} 在库内兜底"一单一成功支付"。</p>
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecordEntity> {

    /**
     * 发起支付：CREATED → PAYING 并记录渠道交易号。
     *
     * @param paymentNo        支付单号
     * @param thirdPartyTradeNo 渠道交易号
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE payment_record SET status = 'PAYING', "
            + "third_party_trade_no = #{thirdPartyTradeNo}, version = version + 1 "
            + "WHERE payment_no = #{paymentNo} AND status = 'CREATED'")
    int markPaying(@Param("paymentNo") String paymentNo,
                   @Param("thirdPartyTradeNo") String thirdPartyTradeNo);

    /**
     * 回调成功：CREATED/PAYING → SUCCESS（docs/modules/10 第 7.2 节）。
     *
     * @param paymentNo 支付单号
     * @param paidAt    支付时间
     * @return 影响行数，1 成功 / 0 已 SUCCESS/FAILED/CLOSED
     */
    @Update("UPDATE payment_record SET status = 'SUCCESS', paid_at = #{paidAt}, "
            + "version = version + 1 "
            + "WHERE payment_no = #{paymentNo} AND status IN ('CREATED', 'PAYING')")
    int markSuccess(@Param("paymentNo") String paymentNo,
                    @Param("paidAt") LocalDateTime paidAt);

    /**
     * 关闭支付单（支付超时 / 订单取消）：CREATED/PAYING/FAILED → CLOSED。
     *
     * @param bookingId 订单 ID
     * @return 影响行数
     */
    @Update("UPDATE payment_record SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 "
            + "WHERE booking_id = #{bookingId} AND status IN ('CREATED', 'PAYING', 'FAILED')")
    int closeForBooking(@Param("bookingId") Long bookingId);

    /**
     * 按支付单号查询。
     *
     * @param paymentNo 支付单号
     * @return 支付记录或 null
     */
    @Select("SELECT * FROM payment_record WHERE payment_no = #{paymentNo}")
    PaymentRecordEntity selectByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 订单唯一成功支付（生成列唯一索引保证最多一条）。
     *
     * @param bookingId 订单 ID
     * @return 成功支付记录或 null
     */
    @Select("SELECT * FROM payment_record WHERE booking_id = #{bookingId} AND status = 'SUCCESS'")
    PaymentRecordEntity selectSuccessByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 订单全部支付尝试（详情页流水）。
     *
     * @param bookingId 订单 ID
     * @return 支付记录列表（时间升序）
     */
    @Select("SELECT * FROM payment_record WHERE booking_id = #{bookingId} ORDER BY id ASC")
    List<PaymentRecordEntity> selectByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 下一次尝试序号（当前最大 attempt_no + 1）。
     *
     * @param bookingId 订单 ID
     * @return 尝试序号，无记录返回 0
     */
    @Select("SELECT COALESCE(MAX(attempt_no), 0) FROM payment_record WHERE booking_id = #{bookingId}")
    int selectMaxAttemptNo(@Param("bookingId") Long bookingId);

    /**
     * 幂等键查询（uk_payment_record_idempotency 命中时回放既有支付单）。
     *
     * @param accountId      账号 ID
     * @param idempotencyKey 幂等键
     * @return 支付记录或 null
     */
    @Select("SELECT * FROM payment_record WHERE account_id = #{accountId} "
            + "AND idempotency_key = #{idempotencyKey}")
    PaymentRecordEntity selectByIdempotencyKey(@Param("accountId") Long accountId,
                                               @Param("idempotencyKey") String idempotencyKey);

    /**
     * 分页查询支付记录（运营视角）。
     *
     * @param status 状态过滤，可空
     * @param offset 偏移
     * @param limit  条数
     * @return 支付记录列表（时间倒序）
     */
    @Select("<script>SELECT * FROM payment_record "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY id DESC LIMIT #{offset}, #{limit}</script>")
    List<PaymentRecordEntity> selectPage(@Param("status") String status,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);

    /**
     * 统计支付记录总数（运营分页用）。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Select("<script>SELECT COUNT(*) FROM payment_record "
            + "<where><if test='status != null'>status = #{status}</if></where></script>")
    long count(@Param("status") String status);
}
