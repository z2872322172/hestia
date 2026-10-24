package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewDimensionScoreEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评价维度分数 Mapper。
 */
@Mapper
public interface ReviewDimensionScoreMapper extends BaseMapper<ReviewDimensionScoreEntity> {

    /**
     * 查询评价的全部分维度分数。
     *
     * @param reviewId 评价 ID
     * @return 维度分数列表
     */
    @Select("SELECT * FROM review_dimension_score WHERE review_id = #{reviewId} "
            + "ORDER BY id")
    List<ReviewDimensionScoreEntity> selectByReviewId(@Param("reviewId") Long reviewId);
}
