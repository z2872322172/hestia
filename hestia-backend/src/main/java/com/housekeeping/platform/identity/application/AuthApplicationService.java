package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.AuthenticatedAccount;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.identity.application.view.AccountView;
import com.housekeeping.platform.identity.application.view.LoginView;
import com.housekeeping.platform.identity.domain.AccountStatus;
import com.housekeeping.platform.identity.domain.LoginResultCode;
import com.housekeeping.platform.identity.domain.PasswordPolicy;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.infrastructure.security.IssuedToken;
import com.housekeeping.platform.infrastructure.security.JwtTokenService;
import com.housekeeping.platform.infrastructure.security.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证应用服务：注册、登录、退出、切换角色、改密。
 *
 * <p>几条不能妥协的规则：</p>
 * <ul>
 *   <li>登录失败对外统一提示"账号或密码错误"，不区分账号不存在、密码错误、
 *       角色不可用——否则接口会变成账号枚举器（docs/modules/01 第 6.3 节）；</li>
 *   <li>失败计数与登录日志走独立事务（{@link LoginAttemptService}），
 *       否则抛异常时事务回滚会把失败痕迹一起抹掉；</li>
 *   <li>注册把"账号 + 角色 + 客户档案"放在同一事务，不允许出现半成品数据。</li>
 * </ul>
 */
@Service
@RequiresPersistence
public class AuthApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthApplicationService.class);

    private static final String ACCOUNT_NO_SEQ_KEY = "ACCOUNT";

    private static final String ACCOUNT_NO_PREFIX = "ACC";

    private final AccountMapper accountMapper;

    private final AccountRoleMapper accountRoleMapper;

    private final AccountRoleService accountRoleService;

    private final AccountQueryService accountQueryService;

    private final RoleAuthorizationService roleAuthorizationService;

    private final LoginAttemptService loginAttemptService;

    private final LoginLogService loginLogService;

    private final CustomerProfileService customerProfileService;

    private final BusinessNoGenerator businessNoGenerator;

    private final JwtTokenService jwtTokenService;

    private final TokenRevocationService tokenRevocationService;

    private final PasswordEncoder passwordEncoder;

    public AuthApplicationService(AccountMapper accountMapper,
                                  AccountRoleMapper accountRoleMapper,
                                  AccountRoleService accountRoleService,
                                  AccountQueryService accountQueryService,
                                  RoleAuthorizationService roleAuthorizationService,
                                  LoginAttemptService loginAttemptService,
                                  LoginLogService loginLogService,
                                  CustomerProfileService customerProfileService,
                                  BusinessNoGenerator businessNoGenerator,
                                  JwtTokenService jwtTokenService,
                                  TokenRevocationService tokenRevocationService,
                                  PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.accountRoleService = accountRoleService;
        this.accountQueryService = accountQueryService;
        this.roleAuthorizationService = roleAuthorizationService;
        this.loginAttemptService = loginAttemptService;
        this.loginLogService = loginLogService;
        this.customerProfileService = customerProfileService;
        this.businessNoGenerator = businessNoGenerator;
        this.jwtTokenService = jwtTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 消费者注册。
     *
     * @param countryCode 区号，为空按 +86
     * @param rawPhone    手机号
     * @param rawPassword 密码
     * @param displayName 显示名称
     * @param clientInfo  客户端信息
     * @return 登录结果（注册即登录）
     */
    @Transactional
    public LoginView register(String countryCode, String rawPhone, String rawPassword,
                              String displayName, ClientInfo clientInfo) {
        PhoneNumber phone = parsePhone(countryCode, rawPhone);
        validatePassword(rawPassword, phone);
        if (accountMapper.selectByPhone(phone.countryCode(), phone.number()) != null) {
            throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);
        }

        AccountEntity account = new AccountEntity();
        account.setAccountNo(businessNoGenerator.next(ACCOUNT_NO_SEQ_KEY, ACCOUNT_NO_PREFIX));
        account.setPhoneCountryCode(phone.countryCode());
        account.setPhone(phone.number());
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setDisplayName(displayName);
        account.setStatus(AccountStatus.ACTIVE.name());
        account.setTokenVersion(0L);
        account.setFailedLoginCount(0);
        try {
            accountMapper.insert(account);
        } catch (DuplicateKeyException exception) {
            // 并发注册同一手机号：唯一索引兜底，对外仍是"已注册"
            throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);
        }

        accountRoleService.grant(account.getId(), RoleCode.CUSTOMER, null);
        // 与账号同事务：注册失败不能留下"有账号没客户档案"的数据
        customerProfileService.createForAccount(account.getId());
        loginLogService.record(account.getId(), phone, LoginResultCode.SUCCESS, null,
                clientInfo.ipAddress(), clientInfo.userAgent());

        List<String> roles = accountRoleMapper.selectActiveRoleCodes(account.getId());
        return issueLogin(account, RoleCode.CUSTOMER.name(), roles);
    }

    /**
     * 手机号 + 密码登录。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：失败计数与登录日志走独立事务
     * （{@link LoginAttemptService}），如果外层再开一个事务，外层对 account 行持有的
     * 排他锁会和内层事务的更新互相等待，直接锁等待超时。
     * 登录流程本身没有"多语句必须同时成功"的不变量——每条语句各自原子即可。</p>
     *
     * @param countryCode 区号
     * @param rawPhone    手机号
     * @param rawPassword 密码
     * @param clientInfo  客户端信息
     * @return 登录结果
     */
    public LoginView login(String countryCode, String rawPhone, String rawPassword, ClientInfo clientInfo) {
        PhoneNumber phone = parsePhone(countryCode, rawPhone);
        AccountEntity account = accountMapper.selectByPhone(phone.countryCode(), phone.number());
        if (account == null) {
            // 账号不存在也要留痕：否则针对不存在账号的爆破完全不可见
            loginAttemptService.recordOutcome(null, phone, LoginResultCode.FAILED,
                    "ACCOUNT_NOT_FOUND", clientInfo);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        account = releaseExpiredLock(account);
        AccountStatus status = AccountStatus.valueOf(account.getStatus());
        switch (status) {
            case DISABLED -> {
                loginAttemptService.recordOutcome(account.getId(), phone, LoginResultCode.DISABLED,
                        "ACCOUNT_DISABLED", clientInfo);
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
            }
            case PENDING_ACTIVATION -> {
                loginAttemptService.recordOutcome(account.getId(), phone, LoginResultCode.FAILED,
                        "PENDING_ACTIVATION", clientInfo);
                throw new BusinessException(ErrorCode.AUTH_PENDING_ACTIVATION);
            }
            case LOCKED -> {
                loginAttemptService.recordOutcome(account.getId(), phone, LoginResultCode.LOCKED,
                        "ACCOUNT_LOCKED", clientInfo);
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
            }
            default -> {
                // ACTIVE：继续校验密码
            }
        }

        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            loginAttemptService.recordPasswordFailure(account.getId(), phone, clientInfo);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        List<String> roles = accountRoleMapper.selectActiveRoleCodes(account.getId());
        if (roles.isEmpty()) {
            loginAttemptService.recordOutcome(account.getId(), phone, LoginResultCode.FAILED,
                    "NO_ACTIVE_ROLE", clientInfo);
            throw new BusinessException(ErrorCode.AUTH_ROLE_NOT_GRANTED);
        }
        String activeRole = roleAuthorizationService.pickActiveRole(roles);
        accountMapper.markLoginSuccess(account.getId());
        loginAttemptService.recordOutcome(account.getId(), phone, LoginResultCode.SUCCESS, null, clientInfo);
        return issueLogin(account, activeRole, roles);
    }

    /**
     * 退出登录：撤销当前设备的令牌。
     *
     * @param principal 当前认证主体
     */
    public void logout(AuthenticatedAccount principal) {
        if (principal == null) {
            return;
        }
        tokenRevocationService.revoke(principal.tokenId(), principal.expiresAt());
    }

    /**
     * 当前账号信息。
     *
     * @param accountId  账号 ID
     * @param activeRole 当前活动角色
     * @return 账号视图
     */
    @Transactional(readOnly = true)
    public AccountView me(long accountId, String activeRole) {
        AccountEntity account = accountQueryService.requireById(accountId);
        return accountQueryService.toView(account, activeRole,
                accountRoleMapper.selectActiveRoleCodes(accountId));
    }

    /**
     * 切换活动角色并重新签发令牌。
     *
     * @param accountId  账号 ID
     * @param targetRole 目标角色
     * @return 新的登录结果
     */
    @Transactional
    public LoginView switchRole(long accountId, String targetRole) {
        AccountEntity account = accountQueryService.requireById(accountId);
        if (!AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        RoleCode role = RoleCode.of(targetRole)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "角色编码非法"));
        roleAuthorizationService.requireActiveRole(accountId, role);
        List<String> roles = accountRoleMapper.selectActiveRoleCodes(accountId);
        return issueLogin(account, role.name(), roles);
    }

    /**
     * 修改密码。
     *
     * <p>改密后 {@code token_version + 1}，所有设备上的旧令牌立即失效，
     * 用户必须重新登录——这是"密码可能已泄露"场景下唯一可靠的处理方式。</p>
     *
     * @param accountId      账号 ID
     * @param oldPassword    原密码
     * @param newPassword    新密码
     */
    @Transactional
    public void changePassword(long accountId, String oldPassword, String newPassword) {
        AccountEntity account = accountQueryService.requireById(accountId);
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "原密码不正确");
        }
        PhoneNumber phone = new PhoneNumber(account.getPhoneCountryCode(), account.getPhone());
        validatePassword(newPassword, phone);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_VIOLATION, "新密码不能与原密码相同");
        }
        accountMapper.updatePassword(accountId, passwordEncoder.encode(newPassword));
        LOG.info("账号修改密码，旧令牌已失效 accountId={}", accountId);
    }

    private AccountEntity releaseExpiredLock(AccountEntity account) {
        if (!AccountStatus.LOCKED.name().equals(account.getStatus())) {
            return account;
        }
        if (accountMapper.releaseExpiredLock(account.getId()) > 0) {
            AccountEntity refreshed = accountMapper.selectById(account.getId());
            return refreshed == null ? account : refreshed;
        }
        return account;
    }

    private LoginView issueLogin(AccountEntity account, String activeRole, List<String> roles) {
        IssuedToken token = jwtTokenService.issue(account.getId(), activeRole,
                account.getTokenVersion() == null ? 0L : account.getTokenVersion());
        AccountView view = accountQueryService.toView(account, activeRole, roles);
        return new LoginView(token.token(), token.expiresInSeconds(), view);
    }

    private PhoneNumber parsePhone(String countryCode, String rawPhone) {
        try {
            return PhoneNumber.parse(countryCode, rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
    }

    private void validatePassword(String rawPassword, PhoneNumber phone) {
        try {
            PasswordPolicy.validate(rawPassword, phone);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_VIOLATION, exception.getMessage());
        }
    }
}
