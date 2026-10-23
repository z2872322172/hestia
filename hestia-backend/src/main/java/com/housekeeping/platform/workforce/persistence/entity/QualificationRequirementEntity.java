package com.housekeeping.platform.workforce.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务项目资质要求，对应表 {@code service_qualification_requirement}。
 *
 * <p>由目录模块的服务项目反向决定人员需要哪些证书。放在人员模块是因为
 * 它是"人员资格"的一部分；目录模块只提供 {@code serviceItemId}，不关心证书。</p>
 */
@Data
@TableName("service_qualification_requirement")
public class QualificationRequirementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceItemId;

    /** IDENTITY / HEALTH / BACKGROUND_CHECK / SKILL_CERTIFICATE / SPECIAL_EQUIPMENT / OTHER。 */
    private String certificateType;

    /** 是否必需。为 {@code false} 表示"有则优先"，不阻断派单。 */
    private Boolean required;

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
