package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价维度分数，对应表 {@code review_dimension_score}（docs/modules/11 第 11.3 节）。
 *
 * <p>唯一键 {@code (review_id, dimension_code)}：每条评价每个维度一行。</p>
 */
@Data
@TableName("review_dimension_score")
public class ReviewDimensionScoreEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private String dimensionCode;

    private BigDecimal score;

    private LocalDateTime createdAt;
}
