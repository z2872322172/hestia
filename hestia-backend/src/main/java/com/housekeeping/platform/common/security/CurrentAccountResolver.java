package com.housekeeping.platform.common.security;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 当前账号解析器：把"可能为空"的端口收紧成"必须存在"。
 *
 * <p>所有面向已登录用户的接口都应通过本类取账号 ID，而不是各自判断
 * {@code if (accountId == null)}：漏判一处就会把 {@code null} 传进查询条件，
 * 变成"查全部"或 SQL 报错，前者是越权，后者是可用性问题。</p>
 */
@Component
public class CurrentAccountResolver {

    private final CurrentAccountProvider provider;

    public CurrentAccountResolver(CurrentAccountProvider provider) {
        this.provider = provider;
    }

    /**
     * 取当前账号 ID，未登录直接抛 401。
     *
     * @return 账号 ID
     * @throws BusinessException 未认证
     */
    public long requireAccountId() {
        Long accountId = provider.currentAccountId();
        if (accountId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return accountId;
    }

    /**
     * 取当前活动角色编码，未登录返回 {@code null}。
     *
     * @return 角色编码
     */
    public String currentRoleCode() {
        return provider.currentRoleCode();
    }
}
