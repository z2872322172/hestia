package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.domain.LoginResultCode;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.identity.persistence.entity.AccountLoginLogEntity;
import com.housekeeping.platform.identity.persistence.mapper.AccountLoginLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录日志写入。
 *
 * <p>三条硬约束（docs/modules/01 第 17 节）：</p>
 * <ul>
 *   <li>不写密码、不写完整 Token、不写明文手机号（手机号只落 HMAC）；</li>
 *   <li>账号不存在时 {@code accountId} 留空，但仍然记录——否则爆破无主账号就完全没有痕迹；</li>
 *   <li>写日志失败不能影响登录主流程，因此异常只记 WARN 不外抛。</li>
 * </ul>
 */
@Service
@RequiresPersistence
public class LoginLogService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginLogService.class);

    private static final int MAX_USER_AGENT_LENGTH = 512;

    private static final int MAX_IP_LENGTH = 45;

    private final AccountLoginLogMapper loginLogMapper;

    private final LoginKeyHasher loginKeyHasher;

    public LoginLogService(AccountLoginLogMapper loginLogMapper, LoginKeyHasher loginKeyHasher) {
        this.loginLogMapper = loginLogMapper;
        this.loginKeyHasher = loginKeyHasher;
    }

    /**
     * 记录一次登录尝试。
     *
     * @param accountId  账号 ID，账号不存在时传 {@code null}
     * @param phone      归一化手机号
     * @param result     结果
     * @param reasonCode 失败原因码，成功时为 {@code null}
     * @param clientIp   客户端 IP
     * @param userAgent  客户端 UA
     */
    public void record(Long accountId, PhoneNumber phone, LoginResultCode result,
                       String reasonCode, String clientIp, String userAgent) {
        try {
            AccountLoginLogEntity entity = new AccountLoginLogEntity();
            entity.setAccountId(accountId);
            entity.setLoginKeyHash(loginKeyHasher.hash(phone));
            entity.setResult(result.name());
            entity.setReasonCode(reasonCode);
            entity.setIpAddress(truncate(clientIp, MAX_IP_LENGTH));
            entity.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
            entity.setTraceId(RequestContext.getTraceId());
            loginLogMapper.insert(entity);
        } catch (RuntimeException exception) {
            // 日志写失败不能阻断登录：可用性优先，但必须留下服务端痕迹
            LOG.warn("登录日志写入失败 accountId={} result={}", accountId, result, exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
