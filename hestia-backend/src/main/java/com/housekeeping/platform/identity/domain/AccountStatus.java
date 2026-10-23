package com.housekeeping.platform.identity.domain;

/**
 * 账号状态（docs/modules/01 第 5.1 节）。
 *
 * <p>与数据库 {@code chk_account_status} 的取值严格一一对应；
 * 新增状态必须先写 Flyway 迁移，再改本枚举，两边不允许单独演进。</p>
 */
public enum AccountStatus {

    /** 已创建，等待设置密码或完成激活。 */
    PENDING_ACTIVATION,

    /** 正常使用。 */
    ACTIVE,

    /** 因连续登录失败或风险临时锁定。 */
    LOCKED,

    /** 被管理员停用。 */
    DISABLED;

    /**
     * 是否允许发起登录。
     *
     * <p>{@link #LOCKED} 需要结合 {@code locked_until} 判断是否已到期，
     * 因此这里只回答"状态本身是否天然禁止登录"。</p>
     *
     * @return 允许登录返回 {@code true}
     */
    public boolean allowsLogin() {
        return this == ACTIVE;
    }
}
