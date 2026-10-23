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
 * 服务规格值（加项），对应表 {@code service_item_option_value}。
 *
 * <p>{@code numericValue} 有两个用途：面积档位用它作为选档依据，
 * 其他规格可以留空。金额与时长增量都在服务端算，前端只负责展示。</p>
 */
@Data
@TableName("service_item_option_value")
public class ServiceItemOptionValueEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long optionId;

    private String valueCode;

    private String valueName;

    /** 数值型规格值：面积档位填面积，其他可空。 */
    private BigDecimal numericValue;

    private BigDecimal extraPrice;

    private Integer durationDelta;

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
