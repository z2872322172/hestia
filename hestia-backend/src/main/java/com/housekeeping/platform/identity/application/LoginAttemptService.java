package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.domain.LoginResultCode;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录失败计数与登录日志的独立事务写入。
 *
 * <p>为什么必须 {@code REQUIRES_NEW}：登录失败最终会抛
 * {@code AUTH_INVALID_CREDENTIALS}，如果失败计数写在登录主事务里，
 * 异常会让事务回滚——<b>失败次数被一起抹掉，锁定永远不会触发</b>。
 * 这类"异常路径上的写入"必须用独立事务提交。</p>
 */
@Service
@RequiresPersistence
public class LoginAttemptService {

    private static final String REASON_INVALID_PASSWORD = "INVALID_PASSWORD";

    private final AccountMapper accountMapper;

    private final LoginLogService loginLogService;

    private final LoginSecurityProperties loginSecurityProperties;

    public LoginAttemptService(AccountMapper accountMapper, LoginLogService loginLogService,
                               LoginSecurityProperties loginSecurityProperties) {
        this.accountMapper = accountMapper;
        this.loginLogService = loginLogService;
        this.loginSecurityProperties = loginSecurityProperties;
    }

    /**
     * 记录一次密码错误，并在达到阈值时锁定账号。
     *
     * <p>计数用 SQL 原子自增，随后回读真实值：不能拿调用方手里那个可能过期的
     * {@code failedLoginCount} 加一，否则并发爆破时永远差几次。</p>
     *
     * @param accountId 账号 ID
     * @param phone     手机号
     * @param clientInfo 客户端信息
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPasswordFailure(long accountId, PhoneNumber phone, ClientInfo clientInfo) {
        accountMapper.incrementFailedLoginCount(accountId);
        AccountEntity fresh = accountMapper.selectById(accountId);
        int failedCount = fresh == null || fresh.getFailedLoginCount() == null ? 1 : fresh.getFailedLoginCount();
        if (failedCount >= loginSecurityProperties.getMaxFailedAttempts()) {
            accountMapper.lockAccount(accountId, failedCount,
                    LocalDateTime.now().plusMinutes(loginSecurityProperties.getLockMinutes()));
        }
        loginLogService.record(accountId, phone, LoginResultCode.FAILED, REASON_INVALID_PASSWORD,
                clientInfo.ipAddress(), clientInfo.userAgent());
    }

    /**
     * 独立事务写一条登录结果日志。
     *
     * @param accountId  账号 ID，可为 {@code null}
     * @param phone      手机号
     * @param result     结果
     * @param reasonCode 原因码
     * @param clientInfo 客户端信息
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(Long accountId, PhoneNumber phone, LoginResultCode result,
                              String reasonCode, ClientInfo clientInfo) {
        loginLogService.record(accountId, phone, result, reasonCode,
                clientInfo.ipAddress(), clientInfo.userAgent());
    }
}
