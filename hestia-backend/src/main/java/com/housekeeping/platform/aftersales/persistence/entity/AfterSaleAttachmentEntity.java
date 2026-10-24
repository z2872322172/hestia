package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后附件，对应表 {@code after_sale_attachment}（docs/modules/11 第 11.7 节）。
 *
 * <p>唯一键 {@code file_id}：同一文件不能重复绑定。</p>
 */
@Data
@TableName("after_sale_attachment")
public class AfterSaleAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private Long fileId;

    /** CUSTOMER_EVIDENCE / HANDLER_EVIDENCE / RESOLUTION_DOCUMENT / OTHER。 */
    private String attachmentType;

    private Long uploadedByAccountId;

    private LocalDateTime createdAt;
}
