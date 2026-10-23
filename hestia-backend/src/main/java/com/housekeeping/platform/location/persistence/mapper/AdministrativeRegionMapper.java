package com.housekeeping.platform.location.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.location.persistence.entity.AdministrativeRegionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 行政区字典 Mapper。
 */
@Mapper
public interface AdministrativeRegionMapper extends BaseMapper<AdministrativeRegionEntity> {

    /**
     * 按国家行政区编码查询。
     *
     * @param regionCode 行政区编码
     * @return 行政区；不存在返回 {@code null}
     */
    @Select("SELECT * FROM administrative_region WHERE region_code = #{regionCode} LIMIT 1")
    AdministrativeRegionEntity selectByCode(@Param("regionCode") String regionCode);

    /**
     * 查询指定上级下的有效子级。
     *
     * @param parentId 上级行政区 ID
     * @return 子级列表，按排序号升序
     */
    @Select("SELECT * FROM administrative_region WHERE parent_id = #{parentId} AND status = 'ACTIVE' "
            + "ORDER BY sort_order ASC, id ASC")
    List<AdministrativeRegionEntity> selectActiveChildren(@Param("parentId") Long parentId);

    /**
     * 查询某层级下的有效行政区。
     *
     * @param regionLevel 层级
     * @return 行政区列表
     */
    @Select("SELECT * FROM administrative_region WHERE region_level = #{regionLevel} AND status = 'ACTIVE' "
            + "ORDER BY sort_order ASC, id ASC")
    List<AdministrativeRegionEntity> selectActiveByLevel(@Param("regionLevel") String regionLevel);
}
