package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchCandidateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 派单候选人员 Mapper。
 *
 * <p>候选状态机：GENERATED → SELECTED（被选中）/ SKIPPED（拒单、超时、
 * 时段被占）→ 改派只遍历 GENERATED 候选，保证同一人员不会被重复派给。</p>
 */
@Mapper
public interface DispatchCandidateMapper extends BaseMapper<DispatchCandidateEntity> {

    /**
     * 按任务查询候选人（评分名次升序）。
     *
     * @param dispatchJobId 任务 ID
     * @return 候选列表
     */
    @Select("SELECT * FROM dispatch_candidate WHERE dispatch_job_id = #{dispatchJobId} "
            + "ORDER BY rank_no ASC")
    List<DispatchCandidateEntity> selectByJobId(@Param("dispatchJobId") Long dispatchJobId);

    /**
     * 按任务与人员定位候选（接单/拒单/超时同步候选状态时使用）。
     *
     * @param dispatchJobId 任务 ID
     * @param workerId      人员 ID
     * @return 候选或 null
     */
    @Select("SELECT * FROM dispatch_candidate "
            + "WHERE dispatch_job_id = #{dispatchJobId} AND worker_id = #{workerId}")
    DispatchCandidateEntity selectByJobAndWorker(@Param("dispatchJobId") Long dispatchJobId,
                                                 @Param("workerId") Long workerId);

    /**
     * 任务内候选最大名次（手工派单追加候选时计算名次）。
     *
     * @param dispatchJobId 任务 ID
     * @return 当前最大名次，无候选返回 0
     */
    @Select("SELECT COALESCE(MAX(rank_no), 0) FROM dispatch_candidate "
            + "WHERE dispatch_job_id = #{dispatchJobId}")
    int selectMaxRank(@Param("dispatchJobId") Long dispatchJobId);

    /**
     * 候选标记选中：GENERATED → SELECTED。
     *
     * @param id 候选 ID
     * @return 影响行数，0 表示已被并发处理
     */
    @Update("UPDATE dispatch_candidate SET status = 'SELECTED' "
            + "WHERE id = #{id} AND status = 'GENERATED'")
    int markSelected(@Param("id") Long id);

    /**
     * 候选标记跳过：SELECTED / GENERATED → SKIPPED（拒单、超时、时段被占）。
     *
     * @param id 候选 ID
     * @return 影响行数
     */
    @Update("UPDATE dispatch_candidate SET status = 'SKIPPED' "
            + "WHERE id = #{id} AND status IN ('GENERATED', 'SELECTED')")
    int markSkipped(@Param("id") Long id);
}
