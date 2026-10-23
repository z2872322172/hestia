package com.housekeeping.platform.identity.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号区域数据范围，对应表 {@code account_area_scope}。
 *
 * <p>{@code serviceAreaId} 指向位置模块的服务区域，跨模块只保存 ID。
 * 运营人员没有任何行 = 没有任何业务数据权限，<b>不能</b>解释为"全部区域"。</p>
 */
@Data
@TableName("account_area_scope")
public class AccountAreaScopeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private String roleCode;

    private Long serviceAreaId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
