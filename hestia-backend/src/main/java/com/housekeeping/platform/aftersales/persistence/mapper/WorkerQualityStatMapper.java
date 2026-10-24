package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.WorkerQualityStatEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 人员质量汇总 Mapper。
 *
 * <p>汇总表允许全量重算（docs/modules/11 第 10 节）：重算按
 * 「条件更新计数 + 1 行 upsert」实现，唯一键 {@code worker_id} 兜底并发。</p>
 */
@Mapper
public interface WorkerQualityStatMapper extends BaseMapper<WorkerQualityStatEntity> {

    /**
     * 按人员查询汇总。
     *
     * @param workerId 人员 ID
     * @return 汇总或 null
     */
    @Select("SELECT * FROM worker_quality_stat WHERE worker_id = #{workerId}")
    WorkerQualityStatEntity selectByWorkerId(@Param("workerId") Long workerId);

    /**
     * 更新汇总值（幂等重算）。
     *
     * @param workerId            人员 ID
     * @param reviewCount         可见评价数
     * @param averageRating       平均综合分
     * @param completedOrderCount 已完成订单数
     * @param complaintCount      投诉数
     * @param lateCount           迟到数
     * @param reworkCount         返工数
     * @return 影响行数
     */
    @Update("UPDATE worker_quality_stat SET review_count = #{reviewCount}, "
            + "average_rating = #{averageRating}, completed_order_count = #{completedOrderCount}, "
            + "complaint_count = #{complaintCount}, late_count = #{lateCount}, "
            + "rework_count = #{reworkCount}, last_calculated_at = CURRENT_TIMESTAMP(3), "
            + "version = version + 1 WHERE worker_id = #{workerId}")
    int updateStat(@Param("workerId") long workerId,
                   @Param("reviewCount") int reviewCount,
                   @Param("averageRating") java.math.BigDecimal averageRating,
                   @Param("completedOrderCount") int completedOrderCount,
                   @Param("complaintCount") int complaintCount,
                   @Param("lateCount") int lateCount,
                   @Param("reworkCount") int reworkCount);

    /**
     * 全部已有汇总的人员 ID（定时兜底重算用）。
     *
     * @return 人员 ID 列表
     */
    @Select("SELECT worker_id FROM worker_quality_stat")
    List<Long> selectAllWorkerIds();
}
