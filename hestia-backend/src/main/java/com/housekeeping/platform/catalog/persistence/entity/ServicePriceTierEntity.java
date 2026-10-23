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
 * 阶梯价格，对应表 {@code service_price_tier}。
 *
 * <p>{@code fixedPrice} 与 {@code unitPrice} 至少有一个非空（数据库 CHECK 兜底）。
 * 两者都填时以 {@code fixedPrice} 优先，语义是"这一档就收这个价，不再乘数量"。</p>
 */
@Data
@TableName("service_price_tier")
public class ServicePriceTierEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceItemId;

    /** AREA_TIER / DURATION_TIER / QUANTITY_TIER。 */
    private String tierType;

    private BigDecimal minValue;

    /** {@code null} 表示上不封顶。 */
    private BigDecimal maxValue;

    private BigDecimal fixedPrice;

    private BigDecimal unitPrice;

    private Integer sortOrder;

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
