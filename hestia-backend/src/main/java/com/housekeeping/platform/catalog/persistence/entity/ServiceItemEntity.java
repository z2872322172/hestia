package com.housekeeping.platform.catalog.persistence.entity;

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
 * 服务项目，对应表 {@code service_item}。
 *
 * <p>{@code version} 同时承担两个职责：MyBatis-Plus 的乐观锁版本号，
 * 以及业务上的"服务项目版本"（docs/modules/04 第 6.2 节：改价、改时长、改规格后版本 +1）。
 * 两者语义一致——每次成功修改都是一次新版本，所以共用一个字段而不是加一个
 * {@code business_version} 再手动同步，避免两个版本号打架。</p>
 */
@Data
@TableName("service_item")
public class ServiceItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemNo;

    private Long categoryId;

    private String itemName;

    private String shortDescription;

    private String detailDescription;

    private String coverUrl;

    /** ORDER / HOUR / SQUARE_METER / ITEM。 */
    private String serviceUnit;

    /** FIXED / AREA_TIER / DURATION_TIER / QUANTITY_TIER。 */
    private String pricingMode;

    private BigDecimal basePrice;

    private Integer durationMinutes;

    private BigDecimal minQuantity;

    private BigDecimal maxQuantity;

    private String serviceScope;

    private String serviceStandardSummary;

    /** 乐观锁 + 业务版本号，见类注释。 */
    @Version
    private Long version;

    /** DRAFT / PUBLISHED / OFFLINE。 */
    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime offlineAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
