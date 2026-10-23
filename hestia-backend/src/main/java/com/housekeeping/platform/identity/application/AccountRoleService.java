package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.persistence.entity.AccountRoleEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import com.housekeeping.platform.identity.persistence.mapper.AuthRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账号角色授予与撤销。
 *
 * <p>授予是"幂等 upsert"：同一账号 + 角色只保留一行，
 * 重新授予时把状态改回 ACTIVE 并清空撤销时间，而不是插第二行。
 * 插第二行会让"该账号是否拥有某角色"出现两条互相矛盾的记录，
 * 而唯一索引 {@code uk_account_role} 也会直接拒绝。</p>
 */
@Service
@RequiresPersistence
public class AccountRoleService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String STATUS_SUSPENDED = "SUSPENDED";

    private static final String STATUS_REVOKED = "REVOKED";

    private final AccountRoleMapper accountRoleMapper;

    private final AuthRoleMapper authRoleMapper;

    private final AccountMapper accountMapper;

    public AccountRoleService(AccountRoleMapper accountRoleMapper, AuthRoleMapper authRoleMapper,
                              AccountMapper accountMapper) {
        this.accountRoleMapper = accountRoleMapper;
        this.authRoleMapper = authRoleMapper;
        this.accountMapper = accountMapper;
    }

    /**
     * 授予角色。
     *
     * @param accountId 账号 ID
     * @param role      角色
     * @param grantedBy 授予人账号 ID，系统授予时为 {@code null}
     */
    @Transactional
    public void grant(long accountId, RoleCode role, Long grantedBy) {
        if (authRoleMapper.selectActiveByCode(role.name()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色不可用：" + role.name());
        }
        AccountRoleEntity existing = accountRoleMapper.selectByAccountAndRole(accountId, role.name());
        if (existing == null) {
            AccountRoleEntity entity = new AccountRoleEntity();
            entity.setAccountId(accountId);
            entity.setRoleCode(role.name());
            entity.setStatus(STATUS_ACTIVE);
            entity.setGrantedByAccountId(grantedBy);
            accountRoleMapper.insert(entity);
            return;
        }
        if (STATUS_ACTIVE.equals(existing.getStatus())) {
            return;
        }
        existing.setStatus(STATUS_ACTIVE);
        existing.setRevokedAt(null);
        existing.setGrantedByAccountId(grantedBy);
        existing.setGrantedAt(LocalDateTime.now());
        accountRoleMapper.updateById(existing);
    }

    /**
     * 撤销角色。
     *
     * @param accountId 账号 ID
     * @param role      角色
     */
    @Transactional
    public void revoke(long accountId, RoleCode role) {
        AccountRoleEntity existing = accountRoleMapper.selectByAccountAndRole(accountId, role.name());
        if (existing == null || STATUS_REVOKED.equals(existing.getStatus())) {
            return;
        }
        existing.setStatus(STATUS_REVOKED);
        existing.setRevokedAt(LocalDateTime.now());
        accountRoleMapper.updateById(existing);
    }

    /**
     * 暂停角色（保留授予关系，可恢复）。
     *
     * <p>与 {@link #revoke} 的区别是语义：撤销表示"这个人不再是服务人员"，
     * 暂停表示"暂时不接单"（健康证过期、休假、投诉调查）。用撤销表达暂停，
     * 恢复时就得重新走邀请流程，人员编号与历史技能证书的归属也会变得可疑。</p>
     *
     * <p>同时把 {@code token_version + 1}：角色暂停后旧令牌里还写着 {@code role=WORKER}，
     * 不失效的话他可以继续用旧令牌访问 {@code /api/v1/worker/**}，直到令牌自然过期。
     * 代价是消费者身份也需要重新登录一次——这是可接受的，因为角色状态变了本来就该重新认证。</p>
     *
     * @param accountId 账号 ID
     * @param role      角色
     */
    @Transactional
    public void suspend(long accountId, RoleCode role) {
        AccountRoleEntity existing = accountRoleMapper.selectByAccountAndRole(accountId, role.name());
        if (existing == null || !STATUS_ACTIVE.equals(existing.getStatus())) {
            return;
        }
        existing.setStatus(STATUS_SUSPENDED);
        accountRoleMapper.updateById(existing);
        accountMapper.incrementTokenVersion(accountId);
    }

    /**
     * 账号是否已拥有该角色（含暂停）。
     *
     * @param accountId 账号 ID
     * @param role      角色
     * @return 已授予返回 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean hasRole(long accountId, RoleCode role) {
        return accountRoleMapper.selectByAccountAndRole(accountId, role.name()) != null;
    }
}
