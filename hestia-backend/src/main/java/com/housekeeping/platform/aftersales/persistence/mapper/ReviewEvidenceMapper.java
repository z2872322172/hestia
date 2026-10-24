package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.ReviewEvidenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评价附件 Mapper。
 */
@Mapper
public interface ReviewEvidenceMapper extends BaseMapper<ReviewEvidenceEntity> {

    /**
     * 按评价查询附件（排序号升序）。
     *
     * @param reviewId 评价 ID
     * @return 附件列表
     */
    @Select("SELECT * FROM review_evidence WHERE review_id = #{reviewId} ORDER BY sort_order, id")
    List<ReviewEvidenceEntity> selectByReviewId(@Param("reviewId") Long reviewId);

    /**
     * 按文件查询绑定（幂等校验：同一文件只能绑定一条评价）。
     *
     * @param fileId 文件 ID
     * @return 绑定或 null
     */
    @Select("SELECT * FROM review_evidence WHERE file_id = #{fileId}")
    ReviewEvidenceEntity selectByFileId(@Param("fileId") Long fileId);
}
