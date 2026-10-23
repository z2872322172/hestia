package com.housekeeping.platform.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.catalog.persistence.entity.ServicePriceTierEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 阶梯价格 Mapper。
 */
@Mapper
public interface ServicePriceTierMapper extends BaseMapper<ServicePriceTierEntity> {

    /**
     * 查询服务项目某类阶梯的全部有效档位，按下界升序。
     *
     * <p>排序放在 SQL 里：计价器按顺序取第一个命中的档位，
     * 顺序不稳定会让同一份配置算出不同价格。</p>
     *
     * @param serviceItemId 服务项目 ID
     * @param tierType      阶梯类型
     * @return 档位列表
     */
    @Select("SELECT * FROM service_price_tier WHERE service_item_id = #{serviceItemId} "
            + "AND tier_type = #{tierType} AND status = 'ACTIVE' "
            + "ORDER BY min_value ASC, id ASC")
    List<ServicePriceTierEntity> selectActiveByItemAndType(@Param("serviceItemId") Long serviceItemId,
                                                           @Param("tierType") String tierType);

    /**
     * 查询服务项目的全部有效档位（不限类型）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 档位列表
     */
    @Select("SELECT * FROM service_price_tier WHERE service_item_id = #{serviceItemId} "
            + "AND status = 'ACTIVE' ORDER BY tier_type ASC, min_value ASC, id ASC")
    List<ServicePriceTierEntity> selectActiveByItemId(@Param("serviceItemId") Long serviceItemId);

    /**
     * 删除服务项目的全部档位（改价时整体重建）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM service_price_tier WHERE service_item_id = #{serviceItemId}")
    int deleteByItemId(@Param("serviceItemId") Long serviceItemId);
}
