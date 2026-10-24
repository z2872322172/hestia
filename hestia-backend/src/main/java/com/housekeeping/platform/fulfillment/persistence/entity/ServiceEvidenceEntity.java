package com.housekeeping.platform.fulfillment.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务履约证据，对应表 {@code service_evidence}（docs/modules/09 第 14.3 节）。
 *
 * <p>唯一键 {@code file_id}：一个文件只关联一条证据，重复上传以本记录为准幂等返回。
 * 数据库只保存文件 ID 与业务元数据，文件本体在私有存储（docs 第 9.2 节）。</p>
 */
@Data
@TableName("service_evidence")
public class ServiceEvidenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceRecordId;

    /** 关联的检查项，可空（如服务前后整体照片）。 */
    private Long checklistItemId;

    /** BEFORE_PHOTO / AFTER_PHOTO / CHECKLIST_PHOTO / DOCUMENT / OTHER。 */
    private String evidenceType;

    private Long fileId;

    private String description;

    private Long uploadedByAccountId;

    private LocalDateTime createdAt;
}
