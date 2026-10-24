package com.housekeeping.platform.aftersales.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.aftersales.persistence.entity.AfterSaleAttachmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 售后附件 Mapper。
 */
@Mapper
public interface AfterSaleAttachmentMapper extends BaseMapper<AfterSaleAttachmentEntity> {

    /**
     * 查询工单附件（时间升序）。
     *
     * @param ticketId 工单 ID
     * @return 附件列表
     */
    @Select("SELECT * FROM after_sale_attachment WHERE ticket_id = #{ticketId} ORDER BY id")
    List<AfterSaleAttachmentEntity> selectByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 按文件查询绑定（幂等校验：同一文件只能绑定一个工单）。
     *
     * @param fileId 文件 ID
     * @return 绑定或 null
     */
    @Select("SELECT * FROM after_sale_attachment WHERE file_id = #{fileId}")
    AfterSaleAttachmentEntity selectByFileId(@Param("fileId") Long fileId);
}
