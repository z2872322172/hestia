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
 * 服务规格组，对应表 {@code service_item_option}。
 *
 * <p>{@code optionCode} 是给前端和订单快照用的稳定编码，改展示名不能改编码；
 * 否则历史订单里存的 {@code optionCode} 就指向了一个不存在的规格。</p>
 */
@Data
@TableName("service_item_option")
public class ServiceItemOptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceItemId;

    private String optionCode;

    private String optionName;

    /** SINGLE / MULTIPLE。 */
    private String selectionType;

    /** 是否必选。字段名与列名一致，避免下划线映射歧义。 */
    private Boolean required;

    private Integer minSelect;

    private Integer maxSelect;

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
