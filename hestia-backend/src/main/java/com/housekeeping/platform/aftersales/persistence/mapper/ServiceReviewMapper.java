package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.ServiceReviewEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 订单评价 Mapper。
 *
 * <p>唯一键 {@code booking_id} 保证一单一评价，并发重复评价以唯一键冲突兜底；
 * 隐藏为条件更新，只有 VISIBLE 状态可被隐藏。</p>
 */
@Mapper
public interface ServiceReviewMapper extends BaseMapper<ServiceReviewEntity> {

    /**
     * 按订单查询评价（一单一评价）。
     *
     * @param bookingId 订单 ID
     * @return 评价或 null
     */
    @Select("SELECT * FROM service_review WHERE booking_id = #{bookingId}")
    ServiceReviewEntity selectByBookingId(@Param("bookingId") Long bookingId);

    /**
     * 按评价单号查询。
     *
     * @param reviewNo 评价单号
     * @return 评价或 null
     */
    @Select("SELECT * FROM service_review WHERE review_no = #{reviewNo}")
    ServiceReviewEntity selectByReviewNo(@Param("reviewNo") String reviewNo);

    /**
     * 隐藏违规评价（VISIBLE → HIDDEN，保留原始记录与审计）。
     *
     * @param reviewNo   评价单号
     * @param reason     隐藏原因
     * @param operatorId 运营账号 ID
     * @return 影响行数
     */
    @Update("UPDATE service_review SET status = 'HIDDEN', hidden_reason = #{reason}, "
            + "hidden_by_account_id = #{operatorId}, hidden_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 "
            + "WHERE review_no = #{reviewNo} AND status = 'VISIBLE'")
    int hide(@Param("reviewNo") String reviewNo,
             @Param("reason") String reason,
             @Param("operatorId") long operatorId);

    /**
     * 分页查询客户的评价（含被隐藏的，本人可见）。
     *
     * @param customerId 客户 ID
     * @param offset     偏移
     * @param size       条数
     * @return 评价列表
     */
    @Select("SELECT * FROM service_review WHERE customer_id = #{customerId} "
            + "ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<ServiceReviewEntity> selectPageByCustomer(@Param("customerId") long customerId,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /**
     * 统计客户的评价总数。
     *
     * @param customerId 客户 ID
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM service_review WHERE customer_id = #{customerId}")
    long countByCustomer(@Param("customerId") long customerId);

    /**
     * 分页查询人员的可见评价（供人员查看摘要明细）。
     *
     * @param workerId 人员 ID
     * @param offset   偏移
     * @param size     条数
     * @return 可见评价列表
     */
    @Select("SELECT * FROM service_review WHERE worker_id = #{workerId} "
            + "AND status = 'VISIBLE' ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<ServiceReviewEntity> selectVisibleByWorker(@Param("workerId") long workerId,
                                                    @Param("offset") int offset,
                                                    @Param("size") int size);

    /**
     * 查询某人员的全部可见评价（质量重算用）。
     *
     * @param workerId 人员 ID
     * @return 可见评价列表
     */
    @Select("SELECT overall_score FROM service_review WHERE worker_id = #{workerId} "
            + "AND status = 'VISIBLE'")
    List<ServiceReviewEntity> selectVisibleScoresByWorker(@Param("workerId") long workerId);
}
