package com.housekeeping.platform.identity.domain;

/**
 * 令牌在"当前账号状态"下的校验结论。
 *
 * <p>JWT 只证明"这张票是我们签的"，不证明"这个账号现在还能用"。
 * 每个需要认证的请求都必须再查一次账号状态与 {@code token_version}，
 * 否则管理员停用账号后，对方手里的旧 Token 仍能用到自然过期（最长 120 分钟）。</p>
 */
public enum TokenState {

    /** 令牌有效。 */
    OK,

    /** 账号令牌版本已变更（改密、停用、角色变更），旧 Token 失效。 */
    TOKEN_STALE,

    /** 账号被停用。 */
    ACCOUNT_DISABLED,

    /** 账号处于锁定期。 */
    ACCOUNT_LOCKED,

    /** 账号尚未激活。 */
    ACCOUNT_PENDING,

    /** 令牌里的活动角色已被暂停或撤销。 */
    ROLE_NOT_ACTIVE
}
