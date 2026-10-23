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
 * 人员技能，对应表 {@code worker_skill}。
 *
 * <p>技能绑定到<b>具体服务项目</b>而不是一级分类：分类下的项目价格与作业要求可以差很远
 * （日常保洁 vs 空调深度清洗），按分类授权等于把风险留给现场。
 * {@code service_item_id} 是目录模块的 ID，刻意不建外键——建了就跨模块绑表。</p>
 *
 * <p>{@code verified} 与 {@code status} 都要满足才算可用技能：
 * 审核通过但已停用的技能不能参与派单（人员主动放弃该技能的情形）。</p>
 */
@Data
@TableName("worker_skill")
public class WorkerSkillEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    private Long serviceItemId;

    /** JUNIOR / INTERMEDIATE / SENIOR。 */
    private String skillLevel;

    /** 是否已审核。 */
    private Boolean verified;

    private Long verifiedByAccountId;

    private LocalDateTime verifiedAt;

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
