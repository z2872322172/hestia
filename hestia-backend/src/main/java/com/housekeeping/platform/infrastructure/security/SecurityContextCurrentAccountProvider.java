package com.housekeeping.platform.infrastructure.security;

import com.housekeeping.platform.common.security.AuthenticatedAccount;
import com.housekeeping.platform.common.security.CurrentAccountProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从 Spring Security 上下文读取当前账号。
 *
 * <p>这是 {@link CurrentAccountProvider} 的正式实现，替换批次 0 的"永远返回 null"占位实现。
 * 关键约束：<b>身份只能来自安全上下文</b>，Controller 不得接收前端传入的 accountId，
 * 否则等于把越权开关交给客户端。</p>
 */
@Component
public class SecurityContextCurrentAccountProvider implements CurrentAccountProvider {

    @Override
    public Long currentAccountId() {
        AuthenticatedAccount account = current();
        return account == null ? null : account.accountId();
    }

    @Override
    public String currentRoleCode() {
        AuthenticatedAccount account = current();
        return account == null ? null : account.roleCode();
    }

    /**
     * 取当前认证主体。
     *
     * <p>只认 {@link AuthenticatedAccount}：匿名认证（AnonymousAuthenticationToken）的
     * principal 是字符串 "anonymousUser"，若不判断类型会得到 ClassCastException。</p>
     */
    private AuthenticatedAccount current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AuthenticatedAccount account ? account : null;
    }
}
