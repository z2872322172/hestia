package com.housekeeping.platform.identity.application.contract;

/**
 * 跨模块使用的账号引用。
 *
 * <p>其他模块判断"这个人现在能不能干活"时，只需要账号状态与角色状态，
 * 不需要密码哈希、失败次数、锁定时间这些认证细节。
 * 返回实体会让任何一个模块都有机会读到 {@code password_hash}，
 * 一旦有人图省事直接拿它比对密码，认证逻辑就散到模块外面去了。</p>
 *
 * @param accountId    账号 ID
 * @param accountNo    账号编号
 * @param status       账号状态（{@code ACTIVE} / {@code PENDING_ACTIVATION} / {@code DISABLED} / …）
 * @param workerActive WORKER 角色当前是否为 {@code ACTIVE}
 */
public record AccountRef(long accountId, String accountNo, String status, boolean workerActive) {

    /**
     * 账号是否处于可登录、可作业的状态。
     *
     * <p>{@code PENDING_ACTIVATION} 不算——账号还没设密码，人还没真正入职。</p>
     *
     * @return 状态为 {@code ACTIVE} 返回 {@code true}
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
