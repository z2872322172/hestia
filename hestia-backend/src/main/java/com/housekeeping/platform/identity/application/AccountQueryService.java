package com.housekeeping.platform.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.application.contract.AccountRef;
import com.housekeeping.platform.identity.application.contract.AccountRefQueryService;
import com.housekeeping.platform.identity.application.view.AccountView;
import com.housekeeping.platform.identity.domain.AccountStatus;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.domain.TokenState;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.identity.persistence.entity.AccountRoleEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 账号查询服务（跨模块公开契约 {@code AccountQueryService}）。
 *
 * <p>其他模块需要"这个账号是谁、是什么状态"时只能走本服务，
 * 不允许直接注入 {@code AccountMapper}——否则账号状态的判定规则会散落到各处。</p>
 */
@Service
@RequiresPersistence
public class AccountQueryService implements AccountRefQueryService {

    private final AccountMapper accountMapper;

    private final AccountRoleMapper accountRoleMapper;

    public AccountQueryService(AccountMapper accountMapper, AccountRoleMapper accountRoleMapper) {
        this.accountMapper = accountMapper;
        this.accountRoleMapper = accountRoleMapper;
    }

    /**
     * 按手机号查账号。
     *
     * @param phone 归一化手机号
     * @return 账号
     */
    @Transactional(readOnly = true)
    public Optional<AccountEntity> findByPhone(PhoneNumber phone) {
        return Optional.ofNullable(
                accountMapper.selectByPhone(phone.countryCode(), phone.number()));
    }

    /**
     * 按 ID 取账号。
     *
     * @param accountId 账号 ID
     * @return 账号
     * @throws BusinessException 账号不存在
     */
    @Transactional(readOnly = true)
    public AccountEntity requireById(long accountId) {
        AccountEntity entity = accountMapper.selectById(accountId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "账号不存在");
        }
        return entity;
    }

    /**
     * 按账号编号取账号。
     *
     * @param accountNo 账号编号
     * @return 账号
     * @throws BusinessException 账号不存在
     */
    @Transactional(readOnly = true)
    public AccountEntity requireByAccountNo(String accountNo) {
        AccountEntity entity = accountMapper.selectByAccountNo(accountNo);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "账号不存在");
        }
        return entity;
    }

    /**
     * 查询账号当前有效的角色编码。
     *
     * @param accountId 账号 ID
     * @return 角色编码列表
     */
    @Transactional(readOnly = true)
    public List<String> activeRoleCodes(long accountId) {
        return accountRoleMapper.selectActiveRoleCodes(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountRef requireRefByAccountNo(String accountNo) {
        return toRef(requireByAccountNo(accountNo));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountRef requireRefById(long accountId) {
        return toRef(requireById(accountId));
    }

    /**
     * 实体转跨模块摘要。
     *
     * <p>WORKER 角色单独查一次而不是从 {@code activeRoleCodes} 里找：
     * 后者返回的是"ACTIVE 角色码列表"，把"角色被暂停"和"角色不存在"压成了同一种结果，
     * 而人员暂停需要区分这两种情况——前者可以恢复，后者要走邀请流程。</p>
     */
    private AccountRef toRef(AccountEntity entity) {
        AccountRoleEntity workerRole =
                accountRoleMapper.selectByAccountAndRole(entity.getId(), RoleCode.WORKER.name());
        boolean workerActive = workerRole != null && "ACTIVE".equals(workerRole.getStatus());
        return new AccountRef(entity.getId(), entity.getAccountNo(), entity.getStatus(), workerActive);
    }

    /**
     * 校验令牌在"当前账号状态"下是否仍然有效。
     *
     * <p>判定顺序刻意固定：先账号状态，再令牌版本，最后角色。
     * 顺序不同会导致提示语误导——账号已被停用却提示"令牌失效"，
     * 用户会一直尝试重新登录而永远失败。</p>
     *
     * @param accountId    令牌中的账号 ID
     * @param tokenVersion 令牌中的版本号
     * @param roleCode     令牌中的活动角色
     * @return 校验结论与账号实体
     */
    @Transactional(readOnly = true)
    public TokenValidation validateToken(long accountId, long tokenVersion, String roleCode) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null) {
            return new TokenValidation(TokenState.TOKEN_STALE, null);
        }
        AccountStatus status = AccountStatus.valueOf(account.getStatus());
        if (status == AccountStatus.DISABLED) {
            return new TokenValidation(TokenState.ACCOUNT_DISABLED, account);
        }
        if (status == AccountStatus.PENDING_ACTIVATION) {
            return new TokenValidation(TokenState.ACCOUNT_PENDING, account);
        }
        if (status == AccountStatus.LOCKED
                && account.getLockedUntil() != null
                && account.getLockedUntil().isAfter(LocalDateTime.now())) {
            return new TokenValidation(TokenState.ACCOUNT_LOCKED, account);
        }
        if (account.getTokenVersion() == null || account.getTokenVersion() != tokenVersion) {
            return new TokenValidation(TokenState.TOKEN_STALE, account);
        }
        if (!accountRoleMapper.selectActiveRoleCodes(accountId).contains(roleCode)) {
            return new TokenValidation(TokenState.ROLE_NOT_ACTIVE, account);
        }
        return new TokenValidation(TokenState.OK, account);
    }

    /**
     * 组装账号视图。
     *
     * @param account    账号实体
     * @param activeRole 当前活动角色
     * @param roles      有效角色列表
     * @return 视图
     */
    public AccountView toView(AccountEntity account, String activeRole, List<String> roles) {
        PhoneNumber phone = new PhoneNumber(account.getPhoneCountryCode(), account.getPhone());
        return new AccountView(account.getAccountNo(), account.getDisplayName(), activeRole,
                account.getStatus(), phone.masked(), roles);
    }

    /**
     * 分页查询账号（后台）。
     *
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @param keyword 关键字，匹配账号编号或显示名称；为空则不过滤
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AccountView> page(int page, int size, String keyword) {
        LambdaQueryWrapper<AccountEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(condition -> condition
                    .like(AccountEntity::getAccountNo, trimmed)
                    .or()
                    .like(AccountEntity::getDisplayName, trimmed));
        }
        wrapper.orderByDesc(AccountEntity::getId);
        Page<AccountEntity> result = accountMapper.selectPage(new Page<>(page, size), wrapper);
        List<AccountView> records = result.getRecords().stream()
                .map(account -> toView(account, null, accountRoleMapper.selectActiveRoleCodes(account.getId())))
                .toList();
        return new PageResult<>(records, page, size, result.getTotal());
    }
}
