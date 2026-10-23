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
 * 账号角色，对应表 {@code account_role}。
 *
 * <p>同一账号 + 角色只保留一行：重新授予时把 {@code status} 改回 ACTIVE 并清空
 * {@code revokedAt}，而不是插入第二行——否则"该账号有哪些角色"会出现两条互相矛盾的记录。</p>
 */
@Data
@TableName("account_role")
public class AccountRoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private String roleCode;

    /** ACTIVE / SUSPENDED / REVOKED。 */
    private String status;

    private Long grantedByAccountId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
