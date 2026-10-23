package com.housekeeping.platform.catalog.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务标准检查项，对应表 {@code service_standard_item}。
 *
 * <p>这是<b>模板</b>，不是履约记录。下单时由订单模块复制成快照，
 * 之后运营改模板不会影响已创建的订单（docs/modules/04 第 9 节）。</p>
 */
@Data
@TableName("service_standard_item")
public class ServiceStandardItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceItemId;

    /** 分组名，如"服务前""清洁""收尾"。 */
    private String standardGroup;

    private String itemName;

    private String description;

    /** 是否必做。 */
    private Boolean required;

    /** 是否必须上传照片证据。 */
    private Boolean evidenceRequired;

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
