package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价附件（图片），对应表 {@code review_evidence}（docs/modules/11 第 11.4 节）。
 *
 * <p>唯一键 {@code file_id}：同一文件不能重复绑定到评价。</p>
 */
@Data
@TableName("review_evidence")
public class ReviewEvidenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private Long fileId;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
