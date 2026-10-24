package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewDimensionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评价维度字典 Mapper。
 */
@Mapper
public interface ReviewDimensionMapper extends BaseMapper<ReviewDimensionEntity> {

    /**
     * 查询启用中的维度（按排序号升序）。
     *
     * @return 启用维度列表
     */
    @Select("SELECT * FROM review_dimension WHERE status = 'ACTIVE' ORDER BY sort_order")
    List<ReviewDimensionEntity> selectActive();
}
