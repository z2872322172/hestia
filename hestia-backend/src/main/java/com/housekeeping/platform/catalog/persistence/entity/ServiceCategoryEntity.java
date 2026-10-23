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
 * 服务分类，对应表 {@code service_category}。
 *
 * <p>自引用树结构，层级限制（最多三层）由业务层保证，数据库只保证父分类存在。
 * 不物理删除：停用即可，删掉会让挂在下面的历史服务项目失去分类归属。</p>
 */
@Data
@TableName("service_category")
public class ServiceCategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String categoryNo;

    /** 父分类 ID；一级分类为 {@code null}。 */
    private Long parentId;

    private String categoryName;

    private String iconUrl;

    private String description;

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
