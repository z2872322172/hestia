package com.housekeeping.platform.location.persistence.entity;

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
 * 行政区字典，对应表 {@code administrative_region}。
 *
 * <p>只描述地址结构，不代表公司是否上门服务；是否可服务由 {@code service_area} 决定。
 * 这两个概念一旦混在一张表里，"广州有行政区但暂不提供服务"就无法表达。</p>
 */
@Data
@TableName("administrative_region")
public class AdministrativeRegionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String regionCode;

    private Long parentId;

    /** PROVINCE / CITY / DISTRICT / STREET。 */
    private String regionLevel;

    private String regionName;

    private String shortName;

    private String fullName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** ACTIVE / DISABLED。 */
    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
