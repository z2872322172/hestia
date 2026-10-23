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
 * 邀请与一次性凭证，对应表 {@code account_invitation}。
 *
 * <p>只保存凭证的 SHA-256，明文凭证仅在创建时返回一次。
 * 这样即使数据库被拖库，攻击者也拿不到可直接使用的激活链接。</p>
 */
@Data
@TableName("account_invitation")
public class AccountInvitationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private String roleCode;

    /** WORKER_ACTIVATION / PASSWORD_RESET。 */
    private String purpose;

    private String tokenHash;

    /** PENDING / USED / EXPIRED / REVOKED。 */
    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private Long invitedByAccountId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
