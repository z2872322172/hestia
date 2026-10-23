package com.housekeeping.platform.dispatch.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchCandidateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 派单候选人员 Mapper。
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
}
