package com.housekeeping.platform.workforce.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.workforce.persistence.entity.WorkerServiceAreaEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 人员服务区域 Mapper。
 */
@Mapper
public interface WorkerServiceAreaMapper extends BaseMapper<WorkerServiceAreaEntity> {

    /**
     * 查询人员的服务区域关系（含停用）。
     *
     * @param workerId 人员 ID
     * @return 关系列表
     */
    @Select("SELECT * FROM worker_service_area WHERE worker_id = #{workerId} ORDER BY id ASC")
    List<WorkerServiceAreaEntity> selectByWorkerId(@Param("workerId") Long workerId);

    /**
     * 查询人员已启用的服务区域 ID。
     *
     * @param workerId 人员 ID
     * @return 服务区域 ID 列表
     */
    @Select("SELECT service_area_id FROM worker_service_area "
            + "WHERE worker_id = #{workerId} AND status = 'ACTIVE'")
    List<Long> selectActiveAreaIds(@Param("workerId") Long workerId);

    /**
     * 删除人员的全部区域关系（整体替换）。
     *
     * @param workerId 人员 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM worker_service_area WHERE worker_id = #{workerId}")
    int deleteByWorkerId(@Param("workerId") Long workerId);
}
