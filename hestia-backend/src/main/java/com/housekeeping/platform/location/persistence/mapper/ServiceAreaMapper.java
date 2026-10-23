package com.housekeeping.platform.location.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.location.persistence.entity.ServiceAreaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务区域 Mapper。
 */
@Mapper
public interface ServiceAreaMapper extends BaseMapper<ServiceAreaEntity> {

    /**
     * 按对外编号查询。
     *
     * @param areaNo 服务区域编号
     * @return 服务区域；不存在返回 {@code null}
     */
    @Select("SELECT * FROM service_area WHERE area_no = #{areaNo} LIMIT 1")
    ServiceAreaEntity selectByAreaNo(@Param("areaNo") String areaNo);

    /**
     * 查询城市下的有效服务区域（公共接口用）。
     *
     * <p>只返回 ACTIVE 且在生效期内的区域，并且不返回 {@code priority} 等内部配置。</p>
     *
     * @param cityRegionId 城市行政区 ID
     * @return 服务区域列表
     */
    @Select("SELECT * FROM service_area WHERE city_region_id = #{cityRegionId} AND status = 'ACTIVE' "
            + "AND (effective_from IS NULL OR effective_from <= CURRENT_TIMESTAMP(3)) "
            + "AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP(3)) "
            + "ORDER BY priority DESC, id ASC")
    List<ServiceAreaEntity> selectActiveByCity(@Param("cityRegionId") Long cityRegionId);

    /**
     * 查询城市下的全部服务区域（运营后台用，含草稿与停用）。
     *
     * @param cityRegionId 城市行政区 ID
     * @return 服务区域列表
     */
    @Select("SELECT * FROM service_area WHERE city_region_id = #{cityRegionId} ORDER BY priority DESC, id ASC")
    List<ServiceAreaEntity> selectAllByCity(@Param("cityRegionId") Long cityRegionId);
}
