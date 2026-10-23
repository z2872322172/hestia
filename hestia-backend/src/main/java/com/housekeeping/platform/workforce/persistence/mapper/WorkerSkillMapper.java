package com.housekeeping.platform.workforce.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.workforce.persistence.entity.WorkerSkillEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 人员技能 Mapper。
 */
@Mapper
public interface WorkerSkillMapper extends BaseMapper<WorkerSkillEntity> {

    /**
     * 查询人员的全部技能（含停用，运营视角）。
     *
     * @param workerId 人员 ID
     * @return 技能列表
     */
    @Select("SELECT * FROM worker_skill WHERE worker_id = #{workerId} ORDER BY id ASC")
    List<WorkerSkillEntity> selectByWorkerId(@Param("workerId") Long workerId);

    /**
     * 查询人员<b>已审核且启用</b>的技能所对应的服务项目 ID。
     *
     * <p>资格判定只认这份列表：审核通过但已停用的技能不能派单。</p>
     *
     * @param workerId 人员 ID
     * @return 服务项目 ID 列表
     */
    @Select("SELECT service_item_id FROM worker_skill WHERE worker_id = #{workerId} "
            + "AND verified = 1 AND status = 'ACTIVE'")
    List<Long> selectUsableServiceItemIds(@Param("workerId") Long workerId);

    /**
     * 删除人员的全部技能（整体替换）。
     *
     * @param workerId 人员 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM worker_skill WHERE worker_id = #{workerId}")
    int deleteByWorkerId(@Param("workerId") Long workerId);
}
