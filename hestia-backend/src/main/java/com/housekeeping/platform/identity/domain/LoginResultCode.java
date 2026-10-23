package com.housekeeping.platform.identity.domain;

/**
 * 登录日志结果（对应 {@code account_login_log.result}）。
 *
 * <p>把"锁定"和"停用"单独记成结果，而不是统一记 {@code FAILED}：
 * 安全分析时要能区分"有人在爆破"（FAILED 密集）和"合法用户撞上了锁定"（LOCKED），
 * 两者触发的告警级别完全不同。</p>
 */
public enum LoginResultCode {

    /** 登录成功。 */
    SUCCESS,

    /** 账号或密码错误。 */
    FAILED,

    /** 账号处于锁定期。 */
    LOCKED,

    /** 账号被停用。 */
    DISABLED
}
