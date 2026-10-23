package com.housekeeping.platform.location.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务区域覆盖范围，对应表 {@code service_area_coverage}。
 *
 * <p>停用只改 {@code status}，不删行：删掉就丢失了"这里曾经覆盖过"的配置历史，
 * 区域变更审计和纠纷排查会失去依据。</p>
 */
@Data
@TableName("service_area_coverage")
public class ServiceAreaCoverageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceAreaId;

    private Long regionId;

    /** DISTRICT / STREET，必须与 {@code region.region_level} 一致。 */
    private String coverageLevel;

    /** ACTIVE / DISABLED。 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
