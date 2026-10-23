package com.housekeeping.platform.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务规格组 Mapper。
 */
@Mapper
public interface ServiceItemOptionMapper extends BaseMapper<ServiceItemOptionEntity> {

    /**
     * 查询服务项目的全部有效规格组。
     *
     * @param serviceItemId 服务项目 ID
     * @return 规格组列表
     */
    @Select("SELECT * FROM service_item_option WHERE service_item_id = #{serviceItemId} "
            + "AND status = 'ACTIVE' ORDER BY sort_order ASC, id ASC")
    List<ServiceItemOptionEntity> selectActiveByItemId(@Param("serviceItemId") Long serviceItemId);

    /**
     * 查询服务项目的全部规格组（含停用，运营后台用）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 规格组列表
     */
    @Select("SELECT * FROM service_item_option WHERE service_item_id = #{serviceItemId} "
            + "ORDER BY sort_order ASC, id ASC")
    List<ServiceItemOptionEntity> selectAllByItemId(@Param("serviceItemId") Long serviceItemId);

    /**
     * 删除服务项目的全部规格组（改规格时整体重建）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM service_item_option WHERE service_item_id = #{serviceItemId}")
    int deleteByItemId(@Param("serviceItemId") Long serviceItemId);
}
