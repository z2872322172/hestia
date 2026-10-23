package com.housekeeping.platform.workforce.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.workforce.persistence.entity.QualificationRequirementEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务项目资质要求 Mapper。
 */
@Mapper
public interface QualificationRequirementMapper extends BaseMapper<QualificationRequirementEntity> {

    /**
     * 查询服务项目的全部资质要求（含非必需项）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 要求列表
     */
    @Select("SELECT * FROM service_qualification_requirement "
            + "WHERE service_item_id = #{serviceItemId} AND status = 'ACTIVE' ORDER BY id ASC")
    List<QualificationRequirementEntity> selectActiveByItemId(@Param("serviceItemId") Long serviceItemId);

    /**
     * 删除服务项目的全部资质要求（整体替换）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM service_qualification_requirement WHERE service_item_id = #{serviceItemId}")
    int deleteByItemId(@Param("serviceItemId") Long serviceItemId);
}
