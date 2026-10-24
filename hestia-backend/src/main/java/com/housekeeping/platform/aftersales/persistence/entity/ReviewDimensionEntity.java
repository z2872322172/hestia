package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价维度字典，对应表 {@code review_dimension}（docs/modules/11 第 11.1 节）。
 *
 * <p>主键为维度编码（字典表，迁移脚本种子初始化四个维度），
 * 综合分 = 各维度分按 {@code weight} 加权平均。</p>
 */
@Data
@TableName("review_dimension")
public class ReviewDimensionEntity {

    /** 维度编码：SERVICE / ATTITUDE / QUALITY / PUNCTUALITY。 */
    @TableId(type = IdType.INPUT)
    private String dimensionCode;

    private String dimensionName;

    private BigDecimal maxScore;

    private BigDecimal weight;

    private Integer sortOrder;

    /** ACTIVE / DISABLED。 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
