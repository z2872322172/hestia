package com.housekeeping.platform.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录与凭证策略。
 *
 * <p>这些参数最终应迁到 {@code system_config} 表由运营调整（docs/modules/01 第 7.3 节），
 * 首版放在配置文件里：配置项一旦入库就要配套缓存、刷新与审计，批次 1 还不具备这些能力，
 * 先把值固定下来比造一个改不动的后台页面更诚实。</p>
 */
@ConfigurationProperties(prefix = "app.security.login")
public class LoginSecurityProperties {

    /** 连续失败多少次锁定账号。 */
    private int maxFailedAttempts = 5;

    /** 锁定时长（分钟）。 */
    private int lockMinutes = 15;

    /** 邀请凭证有效期（小时）。 */
    private int invitationTtlHours = 48;

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getInvitationTtlHours() {
        return invitationTtlHours;
    }

    public void setInvitationTtlHours(int invitationTtlHours) {
        this.invitationTtlHours = invitationTtlHours;
    }
}
