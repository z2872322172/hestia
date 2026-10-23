package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.application.view.InvitationView;
import com.housekeeping.platform.identity.application.view.LoginView;
import com.housekeeping.platform.identity.domain.AccountStatus;
import com.housekeeping.platform.identity.domain.PasswordPolicy;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.identity.persistence.entity.AccountInvitationEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountInvitationMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import com.housekeeping.platform.identity.persistence.mapper.AccountRoleMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.infrastructure.security.IssuedToken;
import com.housekeeping.platform.infrastructure.security.JwtTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 服务人员开户与邀请激活。
 *
 * <p>流程（docs/modules/01 第 6.2 节）：</p>
 * <pre>
 * 运营发起邀请 -> 手机号无账号则建 PENDING_ACTIVATION 账号 -> 绑定 WORKER
 * -> 生成一次性凭证（库中只存哈希）-> 服务人员设置密码激活
 * </pre>
 *
 * <p>若手机号已属于消费者账号，则只追加 WORKER 角色，<b>不</b>新建账号——
 * 否则同一个人会有两个账号，历史订单与评价会分裂到两个身份上。</p>
 *
 * <p>人员档案（{@code worker_profile}）属于人员模块，批次 2 创建；
 * 本批只负责账号与角色，账号激活不等于可派单。</p>
 */
@Service
@RequiresPersistence
public class InvitationService {

    private static final String ACCOUNT_NO_SEQ_KEY = "ACCOUNT";

    private static final String ACCOUNT_NO_PREFIX = "ACC";

    private static final String PURPOSE_WORKER_ACTIVATION = "WORKER_ACTIVATION";

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_USED = "USED";

    private static final String STATUS_EXPIRED = "EXPIRED";

    /** 凭证随机字节数：256 bit，足以抵御暴力枚举。 */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final AccountMapper accountMapper;

    private final AccountRoleMapper accountRoleMapper;

    private final AccountInvitationMapper invitationMapper;

    private final AccountRoleService accountRoleService;

    private final AccountQueryService accountQueryService;

    private final LoginSecurityProperties loginSecurityProperties;

    private final BusinessNoGenerator businessNoGenerator;

    private final JwtTokenService jwtTokenService;

    private final PasswordEncoder passwordEncoder;

    public InvitationService(AccountMapper accountMapper,
                             AccountRoleMapper accountRoleMapper,
                             AccountInvitationMapper invitationMapper,
                             AccountRoleService accountRoleService,
                             AccountQueryService accountQueryService,
                             LoginSecurityProperties loginSecurityProperties,
                             BusinessNoGenerator businessNoGenerator,
                             JwtTokenService jwtTokenService,
                             PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.invitationMapper = invitationMapper;
        this.accountRoleService = accountRoleService;
        this.accountQueryService = accountQueryService;
        this.loginSecurityProperties = loginSecurityProperties;
        this.businessNoGenerator = businessNoGenerator;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建服务人员邀请。
     *
     * @param countryCode 区号
     * @param rawPhone    手机号
     * @param displayName 显示名称
     * @param operatorId  发起邀请的运营账号 ID
     * @return 邀请结果（含一次性凭证）
     */
    @Transactional
    public InvitationView createWorkerInvitation(String countryCode, String rawPhone,
                                                 String displayName, Long operatorId) {
        PhoneNumber phone = parsePhone(countryCode, rawPhone);
        AccountEntity account = accountMapper.selectByPhone(phone.countryCode(), phone.number());
        if (account == null) {
            account = createPendingAccount(phone, displayName);
        }
        if (AccountStatus.DISABLED.name().equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (!accountRoleService.hasRole(account.getId(), RoleCode.WORKER)) {
            accountRoleService.grant(account.getId(), RoleCode.WORKER, operatorId);
        }
        if (AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            // 已有密码的账号无需激活，直接具备服务人员身份
            return new InvitationView(null, null, account.getAccountNo(), false,
                    "账号已激活，已授予服务人员身份");
        }

        invitationMapper.revokePending(account.getId(), PURPOSE_WORKER_ACTIVATION);
        String rawToken = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(loginSecurityProperties.getInvitationTtlHours());

        AccountInvitationEntity invitation = new AccountInvitationEntity();
        invitation.setAccountId(account.getId());
        invitation.setRoleCode(RoleCode.WORKER.name());
        invitation.setPurpose(PURPOSE_WORKER_ACTIVATION);
        invitation.setTokenHash(sha256Hex(rawToken));
        invitation.setStatus(STATUS_PENDING);
        invitation.setExpiresAt(expiresAt);
        invitation.setInvitedByAccountId(operatorId);
        invitationMapper.insert(invitation);

        return new InvitationView(rawToken, expiresAt, account.getAccountNo(), true,
                "请将激活凭证一次性转达给服务人员");
    }

    /**
     * 服务人员激活并设置密码。
     *
     * @param rawToken    一次性凭证
     * @param rawPassword 新密码
     * @return 登录结果（激活即登录）
     */
    @Transactional
    public LoginView activateWorker(String rawToken, String rawPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVITATION_INVALID);
        }
        AccountInvitationEntity invitation = invitationMapper.selectByTokenHash(sha256Hex(rawToken));
        if (invitation == null || !PURPOSE_WORKER_ACTIVATION.equals(invitation.getPurpose())) {
            throw new BusinessException(ErrorCode.AUTH_INVITATION_INVALID);
        }
        if (STATUS_USED.equals(invitation.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_INVITATION_INVALID);
        }
        if (!STATUS_PENDING.equals(invitation.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_INVITATION_INVALID);
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(STATUS_EXPIRED);
            invitationMapper.updateById(invitation);
            throw new BusinessException(ErrorCode.AUTH_INVITATION_EXPIRED);
        }

        AccountEntity account = accountQueryService.requireById(invitation.getAccountId());
        PhoneNumber phone = new PhoneNumber(account.getPhoneCountryCode(), account.getPhone());
        validatePassword(rawPassword, phone);

        // 先校验密码再消费凭证：密码不合规不应该把凭证烧掉
        if (invitationMapper.consume(invitation.getId()) == 0) {
            throw new BusinessException(ErrorCode.AUTH_INVITATION_INVALID);
        }
        if (accountMapper.activateWithPassword(account.getId(), passwordEncoder.encode(rawPassword)) == 0) {
            throw new BusinessException(ErrorCode.AUTH_INVITATION_INVALID, "账号状态已变化，请联系运营重新发起邀请");
        }

        AccountEntity activated = accountQueryService.requireById(account.getId());
        List<String> roles = accountRoleMapper.selectActiveRoleCodes(activated.getId());
        IssuedToken token = jwtTokenService.issue(activated.getId(), RoleCode.WORKER.name(),
                activated.getTokenVersion() == null ? 0L : activated.getTokenVersion());
        return new LoginView(token.token(), token.expiresInSeconds(),
                accountQueryService.toView(activated, RoleCode.WORKER.name(), roles));
    }

    private AccountEntity createPendingAccount(PhoneNumber phone, String displayName) {
        AccountEntity account = new AccountEntity();
        account.setAccountNo(businessNoGenerator.next(ACCOUNT_NO_SEQ_KEY, ACCOUNT_NO_PREFIX));
        account.setPhoneCountryCode(phone.countryCode());
        account.setPhone(phone.number());
        account.setDisplayName(displayName);
        account.setStatus(AccountStatus.PENDING_ACTIVATION.name());
        account.setTokenVersion(0L);
        account.setFailedLoginCount(0);
        try {
            accountMapper.insert(account);
            return account;
        } catch (DuplicateKeyException exception) {
            // 并发邀请同一手机号：唯一索引兜底，回读已存在账号
            AccountEntity existing = accountMapper.selectByPhone(phone.countryCode(), phone.number());
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
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
