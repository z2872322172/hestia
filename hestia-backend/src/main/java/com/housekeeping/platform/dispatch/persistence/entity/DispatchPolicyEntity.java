package com.housekeeping.platform.dispatch.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单评分策略，对应表 {@code dispatch_policy}（docs/modules/08 第 13.1 节）。
 *
 * <p>同一时刻最多一个 {@code ACTIVE} 策略，由应用层保证；候选评分读取时只取 ACTIVE。</p>
 */
@Data
@TableName("dispatch_policy")
public class DispatchPolicyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String policyNo;

    private String policyName;

    /** 评分权重 JSON，如 {"area_score":25,...}。 */
    private String weightsJson;

    private Integer maxCandidates;

    private Integer acceptTimeoutMinutes;

    /** DRAFT / ACTIVE / RETIRED。 */
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
