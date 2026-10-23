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
 * 角色字典，对应表 {@code auth_role}。
 *
 * <p>主键是 {@code role_code}（字符串），不是自增 ID：角色编码是跨模块契约
 * （{@code account_role}、{@code account_area_scope} 都按编码关联），
 * 用自增 ID 会让"编码"变成可改的普通字段。</p>
 */
@Data
@TableName("auth_role")
public class AuthRoleEntity {

    @TableId(value = "role_code", type = IdType.INPUT)
    private String roleCode;

    private String roleName;

    /** EXTERNAL / INTERNAL。 */
    private String roleType;

    /** SELF / AREA / ALL。 */
    private String dataScopeType;

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
