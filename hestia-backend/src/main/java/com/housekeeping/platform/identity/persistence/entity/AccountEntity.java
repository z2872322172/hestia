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
 * 登录账号，对应表 {@code account}。
 *
 * <p>不设 {@code deleted}：账号是审计与责任追溯的锚点，只能停用不能删除。
 * 一旦允许删除，历史订单、审计日志里的 {@code operator_account_id} 会指向不存在的行。</p>
 */
@Data
@TableName("account")
public class AccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountNo;

    private String phoneCountryCode;

    /** 纯数字手机号，与 phoneCountryCode 组成唯一键。 */
    private String phone;

    /** BCrypt 结果；待激活账号为 null。 */
    private String passwordHash;

    private String displayName;

    private String avatarUrl;

    /** 见 {@code AccountStatus}。 */
    private String status;

    /** 令牌版本：+1 即令该账号已签发的全部 Token 失效。 */
    private Long tokenVersion;

    private Integer failedLoginCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
