package com.housekeeping.platform.operations.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 非敏感系统配置，对应表 {@code system_config}（V1 技术底座已建表，docs/modules/13 第 10 节）。
 *
 * <p>只保存非敏感参数；数据库密码、JWT 密钥、支付密钥、MinIO Secret 一律不入库。
 * 修改必须带 {@code expectedVersion} 条件更新并写审计。</p>
 */
@Data
@TableName("system_config")
public class SystemConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，全局唯一。 */
    private String configKey;

    /** 配置名称。 */
    private String configName;

    /** 配置分组。 */
    private String configGroup;

    /** 值类型：STRING/INTEGER/DECIMAL/BOOLEAN/JSON。 */
    private String valueType;

    /** 配置值，统一 JSON 编码。 */
    private String valueJson;

    /** 配置说明。 */
    private String description;

    /** ACTIVE/DISABLED。 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    /** 乐观锁版本号。 */
    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
