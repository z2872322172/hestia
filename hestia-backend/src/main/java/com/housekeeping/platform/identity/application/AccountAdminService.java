package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.application.view.AccountDetailView;
import com.housekeeping.platform.identity.application.view.AccountView;
import com.housekeeping.platform.identity.domain.AccountStatus;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.persistence.entity.AccountAreaScopeEntity;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.identity.persistence.entity.AccountRoleEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountAreaScopeMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import com.housekeeping.platform.location.application.ServiceAreaQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台账号管理。
 *
 * <p>停用、启用、调整角色都会 {@code token_version + 1}：
 * 权限变更必须立刻生效，不能让对方继续用旧令牌行使已撤销的权限。</p>
 */
@Service
@RequiresPersistence
public class AccountAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAdminService.class);

    private final AccountMapper accountMapper;

    private final AccountRoleMapper accountRoleMapper;

    private final AccountAreaScopeMapper areaScopeMapper;

    private final AccountQueryService accountQueryService;

    private final AccountRoleService accountRoleService;

    private final RoleAuthorizationService roleAuthorizationService;

    private final ServiceAreaQueryService serviceAreaQueryService;

    public AccountAdminService(AccountMapper accountMapper,
                               AccountRoleMapper accountRoleMapper,
                               AccountAreaScopeMapper areaScopeMapper,
                               AccountQueryService accountQueryService,
                               AccountRoleService accountRoleService,
                               RoleAuthorizationService roleAuthorizationService,
                               ServiceAreaQueryService serviceAreaQueryService) {
        this.accountMapper = accountMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.areaScopeMapper = areaScopeMapper;
        this.accountQueryService = accountQueryService;
        this.accountRoleService = accountRoleService;
        this.roleAuthorizationService = roleAuthorizationService;
        this.serviceAreaQueryService = serviceAreaQueryService;
    }

    /**
     * 分页查询账号。
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AccountView> page(int page, int size, String keyword) {
        return accountQueryService.page(page, size, keyword);
    }

    /**
     * 账号详情。
     *
     * @param accountNo 账号编号
     * @return 详情
     */
    @Transactional(readOnly = true)
    public AccountDetailView detail(String accountNo) {
        AccountEntity account = accountQueryService.requireByAccountNo(accountNo);
        List<AccountDetailView.AccountRoleView> roles = accountRoleMapper
                .selectAllByAccountId(account.getId()).stream()
                .map(role -> new AccountDetailView.AccountRoleView(role.getRoleCode(), role.getStatus(),
                        role.getGrantedAt()))
                .toList();
        List<AccountDetailView.AreaScopeView> scopes = areaScopeMapper
                .selectAllByAccountId(account.getId()).stream()
                .map(scope -> new AccountDetailView.AreaScopeView(scope.getRoleCode(), scope.getServiceAreaId()))
                .toList();
        PhoneNumber phone = new PhoneNumber(account.getPhoneCountryCode(), account.getPhone());
        return new AccountDetailView(account.getAccountNo(), account.getDisplayName(), phone.masked(),
                account.getStatus(), roles, scopes, account.getLastLoginAt());
    }

    /**
     * 停用账号。
     *
     * @param accountNo 账号编号
     */
    @Transactional
    public void disable(String accountNo) {
        AccountEntity account = accountQueryService.requireByAccountNo(accountNo);
        if (AccountStatus.DISABLED.name().equals(account.getStatus())) {
            return;
        }
        accountMapper.updateStatus(account.getId(), AccountStatus.DISABLED.name());
        LOG.info("账号被停用 accountNo={}", accountNo);
    }

    /**
     * 启用账号。
     *
     * @param accountNo 账号编号
     */
    @Transactional
    public void enable(String accountNo) {
        AccountEntity account = accountQueryService.requireByAccountNo(accountNo);
        if (AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            return;
        }
        accountMapper.updateStatus(account.getId(), AccountStatus.ACTIVE.name());
        LOG.info("账号被启用 accountNo={}", accountNo);
    }

    /**
     * 调整账号角色（整体覆盖）。
     *
     * @param accountNo  账号编号
     * @param roleCodes  目标角色编码
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void updateRoles(String accountNo, List<String> roleCodes, Long operatorId) {
        AccountEntity account = accountQueryService.requireByAccountNo(accountNo);
        List<RoleCode> targets = roleAuthorizationService.parseRoles(roleCodes);
        roleAuthorizationService.assertCombinable(targets);

        Set<String> current = accountRoleMapper.selectAllByAccountId(account.getId()).stream()
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .map(AccountRoleEntity::getRoleCode)
                .collect(Collectors.toSet());
        Set<String> targetCodes = targets.stream().map(RoleCode::name).collect(Collectors.toSet());

        for (RoleCode role : targets) {
            if (!current.contains(role.name())) {
                accountRoleService.grant(account.getId(), role, operatorId);
            }
        }
        current.stream()
                .filter(code -> !targetCodes.contains(code))
                .forEach(code -> RoleCode.of(code).ifPresent(role -> accountRoleService.revoke(account.getId(), role)));

        // 角色变化必须让旧令牌失效，否则对方还能用旧角色继续访问
        accountMapper.incrementTokenVersion(account.getId());
        LOG.info("账号角色调整 accountNo={} roles={}", accountNo, targetCodes);
    }

    /**
     * 调整运营区域范围（整体覆盖）。
     *
     * @param accountNo       账号编号
     * @param roleCode        角色编码，必须是有 AREA 数据范围的角色
     * @param serviceAreaIds  服务区域 ID 列表
     */
    @Transactional
    public void updateAreaScopes(String accountNo, String roleCode, List<Long> serviceAreaIds) {
        AccountEntity account = accountQueryService.requireByAccountNo(accountNo);
        RoleCode role = RoleCode.of(roleCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "角色编码非法"));
        if (role != RoleCode.OPERATOR) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只有运营人员需要配置区域范围");
        }
        roleAuthorizationService.requireActiveRole(account.getId(), role);

        List<Long> distinctIds = serviceAreaIds == null ? List.of()
                : serviceAreaIds.stream().distinct().toList();
        // 归档或未启用的区域不能继续授权给运营人员（docs/modules/03 第 17.2 节）
        distinctIds.forEach(serviceAreaQueryService::requireActive);

        areaScopeMapper.deleteByAccountAndRole(account.getId(), role.name());
        for (Long serviceAreaId : distinctIds) {
            AccountAreaScopeEntity entity = new AccountAreaScopeEntity();
            entity.setAccountId(account.getId());
            entity.setRoleCode(role.name());
            entity.setServiceAreaId(serviceAreaId);
            areaScopeMapper.insert(entity);
        }
        accountMapper.incrementTokenVersion(account.getId());
        LOG.info("账号区域范围调整 accountNo={} areas={}", accountNo, distinctIds);
    }
}
