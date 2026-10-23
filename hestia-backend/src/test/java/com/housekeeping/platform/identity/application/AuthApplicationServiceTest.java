package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.identity.application.view.AccountView;
import com.housekeeping.platform.identity.application.view.LoginView;
import com.housekeeping.platform.identity.domain.AccountStatus;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.infrastructure.security.JwtProperties;
import com.housekeeping.platform.infrastructure.security.JwtTokenService;
import com.housekeeping.platform.infrastructure.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试（Mapper 全部 mock，不连数据库）。
 *
 * <p>覆盖 docs/07 第 15.2 节点名的"注册登录、手机号唯一、登录失败计数"三条，
 * 并把"失败必须留痕"作为断言：登录失败如果没写失败计数，
 * 锁定策略就永远不会触发，而这正是最容易在重构中丢掉的逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    private static final String PHONE = "13800138000";

    private static final String RAW_PASSWORD = "Hestia2026";

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountRoleMapper accountRoleMapper;

    @Mock
    private AccountRoleService accountRoleService;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private RoleAuthorizationService roleAuthorizationService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private LoginLogService loginLogService;

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @Mock
    private TokenRevocationService tokenRevocationService;

    private PasswordEncoder passwordEncoder;

    private JwtTokenService jwtTokenService;

    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789-abcdefghijkl");
        properties.setAccessTokenTtl(Duration.ofMinutes(30));
        properties.setIssuer("hestia-test");
        jwtTokenService = new JwtTokenService(properties);
        service = new AuthApplicationService(accountMapper, accountRoleMapper, accountRoleService,
                accountQueryService, roleAuthorizationService, loginAttemptService, loginLogService,
                customerProfileService, businessNoGenerator, jwtTokenService, tokenRevocationService,
                passwordEncoder);
    }

    @Test
    @DisplayName("手机号不存在时统一返回账号或密码错误，并留下登录日志")
    void loginWithUnknownPhoneFails() {
        when(accountMapper.selectByPhone("+86", PHONE)).thenReturn(null);

        assertThatThrownBy(() -> service.login("+86", PHONE, RAW_PASSWORD, ClientInfo.empty()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);

        verify(loginAttemptService).recordOutcome(eq(null), any(PhoneNumber.class),
                eq(com.housekeeping.platform.identity.domain.LoginResultCode.FAILED),
                eq("ACCOUNT_NOT_FOUND"), any(ClientInfo.class));
    }

    @Test
    @DisplayName("密码错误时累加失败次数")
    void loginWithWrongPasswordRecordsFailure() {
        AccountEntity account = activeAccount();
        when(accountMapper.selectByPhone("+86", PHONE)).thenReturn(account);

        assertThatThrownBy(() -> service.login("+86", PHONE, "WrongPassword1", ClientInfo.empty()))
                .isInstanceOf(BusinessException.class);

        verify(loginAttemptService).recordPasswordFailure(eq(account.getId()), any(PhoneNumber.class),
                any(ClientInfo.class));
        verify(accountMapper, never()).markLoginSuccess(anyLong());
    }

    @Test
    @DisplayName("登录成功签发令牌并返回活动角色")
    void loginSucceeds() {
        AccountEntity account = activeAccount();
        when(accountMapper.selectByPhone("+86", PHONE)).thenReturn(account);
        when(accountRoleMapper.selectActiveRoleCodes(account.getId())).thenReturn(List.of("CUSTOMER"));
        when(roleAuthorizationService.pickActiveRole(List.of("CUSTOMER"))).thenReturn("CUSTOMER");
        when(accountQueryService.toView(any(AccountEntity.class), eq("CUSTOMER"), any()))
                .thenReturn(new AccountView("ACC202610230001", "张三", "CUSTOMER", "ACTIVE",
                        "138****8000", List.of("CUSTOMER")));

        LoginView view = service.login("+86", PHONE, RAW_PASSWORD, ClientInfo.empty());

        assertThat(view.accessToken()).isNotBlank();
        assertThat(view.account().activeRole()).isEqualTo("CUSTOMER");
        assertThat(jwtTokenService.parse(view.accessToken()).accountId()).isEqualTo(account.getId());
        verify(accountMapper).markLoginSuccess(account.getId());
    }

    @Test
    @DisplayName("停用账号无法登录")
    void disabledAccountCannotLogin() {
        AccountEntity account = activeAccount();
        account.setStatus(AccountStatus.DISABLED.name());
        when(accountMapper.selectByPhone("+86", PHONE)).thenReturn(account);

        assertThatThrownBy(() -> service.login("+86", PHONE, RAW_PASSWORD, ClientInfo.empty()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED);
    }

    @Test
    @DisplayName("手机号已注册时注册被拒绝")
    void registerRejectsDuplicatePhone() {
        when(accountMapper.selectByPhone("+86", PHONE)).thenReturn(activeAccount());

        assertThatThrownBy(() -> service.register("+86", PHONE, RAW_PASSWORD, "张三", ClientInfo.empty()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);

        verify(customerProfileService, never()).createForAccount(anyLong());
    }

    @Test
    @DisplayName("注册同时创建账号、CUSTOMER 角色与客户档案")
    void registerCreatesAccountRoleAndProfile() {
        when(accountMapper.selectByPhone("+86", PHONE)).thenReturn(null);
        when(businessNoGenerator.next("ACCOUNT", "ACC")).thenReturn("ACC202610230001");
        // 真实环境由 MyBatis-Plus 回填自增主键，mock 需要显式模拟这一行为
        when(accountMapper.insert(any(AccountEntity.class))).thenAnswer(invocation -> {
            AccountEntity inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });
        when(accountRoleMapper.selectActiveRoleCodes(anyLong())).thenReturn(List.of("CUSTOMER"));
        when(accountQueryService.toView(any(AccountEntity.class), anyString(), any()))
                .thenReturn(new AccountView("ACC202610230001", "张三", "CUSTOMER", "ACTIVE",
                        "138****8000", List.of("CUSTOMER")));

        LoginView view = service.register("+86", PHONE, RAW_PASSWORD, "张三", ClientInfo.empty());

        assertThat(view.accessToken()).isNotBlank();
        verify(accountMapper).insert(any(AccountEntity.class));
        verify(accountRoleService, times(1)).grant(anyLong(), eq(RoleCode.CUSTOMER), eq(null));
        verify(customerProfileService, times(1)).createForAccount(anyLong());
    }

    @Test
    @DisplayName("注册密码不合规被拒绝")
    void registerRejectsWeakPassword() {
        assertThatThrownBy(() -> service.register("+86", PHONE, "12345678", "张三", ClientInfo.empty()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_POLICY_VIOLATION);
    }

    private AccountEntity activeAccount() {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setAccountNo("ACC202610230001");
        account.setPhoneCountryCode("+86");
        account.setPhone(PHONE);
        account.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        account.setDisplayName("张三");
        account.setStatus(AccountStatus.ACTIVE.name());
        account.setTokenVersion(0L);
        account.setFailedLoginCount(0);
        return account;
    }
}
