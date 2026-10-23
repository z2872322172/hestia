package com.housekeeping.platform.identity.api.security;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.AuthenticatedAccount;
import com.housekeeping.platform.identity.application.AccountQueryService;
import com.housekeeping.platform.identity.application.TokenValidation;
import com.housekeeping.platform.identity.domain.TokenState;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import com.housekeeping.platform.infrastructure.security.JwtTokenService;
import com.housekeeping.platform.infrastructure.security.TokenPayload;
import com.housekeeping.platform.infrastructure.security.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>每个请求做四件事，任何一步失败都<b>不</b>直接写响应，而是把错误码放进请求属性，
 * 交给 {@code RestAuthenticationEntryPoint} 统一输出 JSON——
 * 这样"未带令牌"和"令牌失效"的响应结构完全一致，前端只需要认 {@code code}。</p>
 *
 * <ol>
 *   <li>验签与解析（是否我们签发、是否过期）；</li>
 *   <li>查撤销列表（是否已退出登录）；</li>
 *   <li>查账号状态与 {@code token_version}（是否被停用、改密、撤角色）；</li>
 *   <li>写入 SecurityContext（principal 为 {@link AuthenticatedAccount}）。</li>
 * </ol>
 *
 * <p>性能取舍：第 3 步每次请求都要查库。账号状态是安全事实，
 * 缓存会让"停用后 5 分钟内仍可访问"成为常态，因此首版不做缓存；
 * 若后续 QPS 上升，正确做法是短 TTL + 变更主动失效，而不是延长 TTL。</p>
 */
@Component
@RequiresPersistence
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因在请求属性中的键，供 AuthenticationEntryPoint 读取。 */
    private static final String AUTH_ERROR_ATTRIBUTE = com.housekeeping.platform.common.api.RequestContext.AUTH_ERROR_ATTRIBUTE;

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    private final AccountQueryService accountQueryService;

    private final TokenRevocationService tokenRevocationService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   AccountQueryService accountQueryService,
                                   TokenRevocationService tokenRevocationService) {
        this.jwtTokenService = jwtTokenService;
        this.accountQueryService = accountQueryService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            TokenPayload payload = jwtTokenService.parse(token);
            if (tokenRevocationService.isRevoked(payload.tokenId())) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN);
                filterChain.doFilter(request, response);
                return;
            }
            TokenValidation validation = accountQueryService.validateToken(
                    payload.accountId(), payload.tokenVersion(), payload.roleCode());
            if (!validation.isValid()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, mapError(validation.state()));
                filterChain.doFilter(request, response);
                return;
            }
            authenticate(validation.account(), payload);
        } catch (BusinessException exception) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, exception.getErrorCode());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(AccountEntity account, TokenPayload payload) {
        AuthenticatedAccount principal = new AuthenticatedAccount(account.getId(), account.getAccountNo(),
                payload.roleCode(), payload.tokenVersion(), payload.tokenId(), payload.expiresAt());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + payload.roleCode())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private ErrorCode mapError(TokenState state) {
        return switch (state) {
            case ACCOUNT_DISABLED -> ErrorCode.AUTH_ACCOUNT_DISABLED;
            case ACCOUNT_LOCKED -> ErrorCode.AUTH_ACCOUNT_LOCKED;
            case ACCOUNT_PENDING -> ErrorCode.AUTH_PENDING_ACTIVATION;
            case ROLE_NOT_ACTIVE -> ErrorCode.AUTH_ROLE_SUSPENDED;
            default -> ErrorCode.AUTH_INVALID_TOKEN;
        };
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
