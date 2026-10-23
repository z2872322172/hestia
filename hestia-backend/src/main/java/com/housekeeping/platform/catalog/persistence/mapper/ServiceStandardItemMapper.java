package com.housekeeping.platform.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.catalog.persistence.entity.ServiceStandardItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务标准检查项 Mapper。
 */
@Mapper
public interface ServiceStandardItemMapper extends BaseMapper<ServiceStandardItemEntity> {

    /**
     * 查询服务项目的有效检查项。
     *
     * @param serviceItemId 服务项目 ID
     * @return 检查项列表
     */
    @Select("SELECT * FROM service_standard_item WHERE service_item_id = #{serviceItemId} "
            + "AND status = 'ACTIVE' ORDER BY sort_order ASC, id ASC")
    List<ServiceStandardItemEntity> selectActiveByItemId(@Param("serviceItemId") Long serviceItemId);

    /**
     * 统计有效检查项数量（发布前校验用）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM service_standard_item "
            + "WHERE service_item_id = #{serviceItemId} AND status = 'ACTIVE'")
    int countActiveByItemId(@Param("serviceItemId") Long serviceItemId);

    /**
     * 删除服务项目的全部检查项（整体替换）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM service_standard_item WHERE service_item_id = #{serviceItemId}")
    int deleteByItemId(@Param("serviceItemId") Long serviceItemId);
}
