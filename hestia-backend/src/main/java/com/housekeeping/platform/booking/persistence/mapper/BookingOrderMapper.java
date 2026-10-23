package com.housekeeping.platform.booking.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约订单 Mapper。
 *
 * <p>状态流转一律走本接口的条件更新（WHERE 带当前状态），影响行数 0 表示
 * 并发竞争失败或状态已变，由调用方决定幂等返回还是抛冲突。</p>
 */
@Mapper
public interface BookingOrderMapper extends BaseMapper<BookingOrderEntity> {

    /**
     * 支付成功确认：PENDING_PAYMENT → CONFIRMED（docs/modules/07 第 8 节）。
     *
     * @param bookingId  订单 ID
     * @param paidAmount 实付金额
     * @param paidAt     支付时间
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET order_status = 'CONFIRMED', payment_status = 'PAID', "
            + "paid_amount = #{paidAmount}, paid_at = #{paidAt}, version = version + 1 "
            + "WHERE id = #{bookingId} AND order_status = 'PENDING_PAYMENT'")
    int markPaid(@Param("bookingId") Long bookingId,
                 @Param("paidAmount") BigDecimal paidAmount,
                 @Param("paidAt") LocalDateTime paidAt);

    /**
     * 取消订单：fromStatus → CANCELED（待支付与已支付取消共用）。
     *
     * @param bookingId  订单 ID
     * @param fromStatus 期望的当前状态
     * @param reasonCode 取消原因编码
     * @param reasonText 取消原因描述
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET order_status = 'CANCELED', "
            + "cancel_reason_code = #{reasonCode}, cancel_reason_text = #{reasonText}, "
            + "canceled_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{bookingId} AND order_status = #{fromStatus}")
    int cancel(@Param("bookingId") Long bookingId,
               @Param("fromStatus") String fromStatus,
               @Param("reasonCode") String reasonCode,
               @Param("reasonText") String reasonText);

    /**
     * 改期：更新预约时间与人员时段绑定（仅 CONFIRMED 可改期）。
     *
     * @param bookingId          订单 ID
     * @param appointmentDate    新服务日期
     * @param serviceWindowId    新时间窗 ID
     * @param serviceWindowName  新时间窗名称快照
     * @param appointmentStart   新开始时间
     * @param appointmentEnd     新结束时间
     * @param workerSlotId       新时段 ID
     * @param currentWorkerId    新人员 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET appointment_date = #{appointmentDate}, "
            + "service_window_id = #{serviceWindowId}, "
            + "service_window_name_snapshot = #{serviceWindowName}, "
            + "appointment_start = #{appointmentStart}, appointment_end = #{appointmentEnd}, "
            + "worker_slot_id = #{workerSlotId}, current_worker_id = #{currentWorkerId}, "
            + "version = version + 1 "
            + "WHERE id = #{bookingId} AND order_status = 'CONFIRMED'")
    int reschedule(@Param("bookingId") Long bookingId,
                   @Param("appointmentDate") LocalDate appointmentDate,
                   @Param("serviceWindowId") Long serviceWindowId,
                   @Param("serviceWindowName") String serviceWindowName,
                   @Param("appointmentStart") LocalDateTime appointmentStart,
                   @Param("appointmentEnd") LocalDateTime appointmentEnd,
                   @Param("workerSlotId") Long workerSlotId,
                   @Param("currentWorkerId") Long currentWorkerId);

    /**
     * 按订单号查询。
     *
     * @param orderNo 订单号
     * @return 订单或 null
     */
    @Select("SELECT * FROM booking_order WHERE order_no = #{orderNo}")
    BookingOrderEntity selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 扫描支付超时订单（定时任务用，docs/modules/07 第 10.1 节）。
     *
     * @param now   当前时间
     * @param limit 单批最大处理数
     * @return 超时未支付订单列表
     */
    @Select("SELECT * FROM booking_order WHERE order_status = 'PENDING_PAYMENT' "
            + "AND payment_deadline < #{now} ORDER BY payment_deadline ASC LIMIT #{limit}")
    List<BookingOrderEntity> selectTimeoutPending(@Param("now") LocalDateTime now,
                                                  @Param("limit") int limit);

    // ------------------------------------------------------------------
    // 履约驱动的状态推进（docs/modules/09 第 5.3 节，履约模块只提交命令不改订单表）
    // ------------------------------------------------------------------

    /**
     * 服务人员出发：CONFIRMED → EN_ROUTE。
     *
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET order_status = 'EN_ROUTE', version = version + 1 "
            + "WHERE id = #{bookingId} AND order_status = 'CONFIRMED'")
    int markEnRoute(@Param("bookingId") Long bookingId);

    /**
     * 开始服务：EN_ROUTE → IN_SERVICE，记录服务开始时间。
     *
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET order_status = 'IN_SERVICE', started_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{bookingId} AND order_status = 'EN_ROUTE'")
    int startService(@Param("bookingId") Long bookingId);

    /**
     * 申请完成：IN_SERVICE → PENDING_CONFIRM。
     *
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET order_status = 'PENDING_CONFIRM', version = version + 1 "
            + "WHERE id = #{bookingId} AND order_status = 'IN_SERVICE'")
    int markPendingConfirm(@Param("bookingId") Long bookingId);

    /**
     * 确认完成：PENDING_CONFIRM → COMPLETED，记录完成时间。
     *
     * @param bookingId 订单 ID
     * @return 影响行数，1 成功 / 0 状态已变
     */
    @Update("UPDATE booking_order SET order_status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE id = #{bookingId} AND order_status = 'PENDING_CONFIRM'")
    int complete(@Param("bookingId") Long bookingId);

    // ------------------------------------------------------------------
    // 派单回填 / 退款摘要
    // ------------------------------------------------------------------

    /**
     * 人员接单回填：assignment_status PENDING_ACCEPT → ACCEPTED，写入当前人员与接单时间。
     *
     * @param bookingId 订单 ID
     * @param workerId  接单人员 ID
     * @return 影响行数，1 成功 / 0 状态已变（含改派后旧人员迟到接单）
     */
    @Update("UPDATE booking_order SET assignment_status = 'ACCEPTED', "
            + "current_worker_id = #{workerId}, assignment_accepted_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 "
            + "WHERE id = #{bookingId} AND order_status = 'CONFIRMED' "
            + "AND assignment_status = 'PENDING_ACCEPT'")
    int markAssignmentAccepted(@Param("bookingId") Long bookingId,
                               @Param("workerId") Long workerId);

    /**
     * 应用退款：累加已退金额并推导支付摘要（全额退 REFUNDED，否则 PARTIALLY_REFUNDED）。
     *
     * <p>WHERE 保证累计退款不超过实付金额——并发两笔退款只有一个成功，
     * 影响行数 0 由调用方转 {@code REFUND_AMOUNT_EXCEEDED}。</p>
     *
     * @param bookingId   订单 ID
     * @param refundAmount 本笔退款金额
     * @return 影响行数，1 成功 / 0 超出可退金额
     */
    @Update("UPDATE booking_order SET refunded_amount = refunded_amount + #{refundAmount}, "
            + "payment_status = CASE WHEN refunded_amount + #{refundAmount} >= paid_amount "
            + "THEN 'REFUNDED' ELSE 'PARTIALLY_REFUNDED' END, version = version + 1 "
            + "WHERE id = #{bookingId} AND paid_amount > 0 "
            + "AND refunded_amount + #{refundAmount} <= paid_amount")
    int applyRefund(@Param("bookingId") Long bookingId,
                    @Param("refundAmount") BigDecimal refundAmount);
}
