package com.housekeeping.platform.identity.application.contract;

/**
 * 账号引用查询契约（供其他模块使用）。
 *
 * <p>只提供"读一个账号摘要"的能力。写入类操作（停用、改角色、提权）必须走
 * 身份模块自己的管理服务，否则角色组合规则（{@code RoleCombinationPolicy}）
 * 和 {@code token_version} 自增会被绕过。</p>
 */
public interface AccountRefQueryService {

    /**
     * 按账号编号取账号摘要。
     *
     * @param accountNo 账号编号
     * @return 账号摘要
     * @throws com.housekeeping.platform.common.exception.BusinessException 账号不存在
     */
    AccountRef requireRefByAccountNo(String accountNo);

    /**
     * 按账号 ID 取账号摘要。
     *
     * @param accountId 账号 ID
     * @return 账号摘要
     * @throws com.housekeeping.platform.common.exception.BusinessException 账号不存在
     */
    AccountRef requireRefById(long accountId);
}
