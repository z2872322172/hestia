package com.housekeeping.platform.identity.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志，对应表 {@code account_login_log}。
 *
 * <p>只追加、不更新、不删除，且<b>没有</b> {@code version} / {@code updated_at}：
 * 登录日志是可被用于取证的事实记录，允许"修改"就等于允许抹掉痕迹。</p>
 *
 * <p>不保存密码、完整 Token、明文手机号：手机号以 HMAC 形式落在 {@code loginKeyHash}。</p>
 */
@Data
@TableName("account_login_log")
public class AccountLoginLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账号不存在时为 null。 */
    private Long accountId;

    private String loginKeyHash;

    /** SUCCESS / FAILED / LOCKED / DISABLED。 */
    private String result;

    private String reasonCode;

    private String ipAddress;

    private String userAgent;

    private String traceId;

    private LocalDateTime createdAt;
}
