package com.housekeeping.platform.identity.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.AuthenticatedAccount;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.identity.api.dto.ActivateWorkerRequest;
import com.housekeeping.platform.identity.api.dto.ChangePasswordRequest;
import com.housekeeping.platform.identity.api.dto.LoginRequest;
import com.housekeeping.platform.identity.api.dto.RegisterRequest;
import com.housekeeping.platform.identity.api.dto.SwitchRoleRequest;
import com.housekeeping.platform.identity.application.AuthApplicationService;
import com.housekeeping.platform.identity.application.ClientInfo;
import com.housekeeping.platform.identity.application.InvitationService;
import com.housekeeping.platform.identity.application.view.AccountView;
import com.housekeeping.platform.identity.application.view.LoginView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>登录、注册、激活是公共接口（{@code /api/v1/auth/customer/register}、
 * {@code /login}、{@code /worker/activate}），其余要求携带 Bearer 令牌。</p>
 *
 * <p>注意：登录失败一律返回 {@code AUTH_INVALID_CREDENTIALS}，
 * 响应中不含"账号是否存在"的差异信息。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiresPersistence
@Tag(name = "认证", description = "注册、登录、退出、角色切换与改密")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    private final InvitationService invitationService;

    private final CurrentAccountResolver currentAccountResolver;

    public AuthController(AuthApplicationService authApplicationService,
                          InvitationService invitationService,
                          CurrentAccountResolver currentAccountResolver) {
        this.authApplicationService = authApplicationService;
        this.invitationService = invitationService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 消费者注册（注册即登录）。
     *
     * @param request     请求体
     * @param httpRequest 原始请求，用于取 IP 与 UA
     * @return 登录结果
     */
    @PostMapping("/customer/register")
    @Operation(summary = "消费者注册")
    public Result<LoginView> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        return Result.ok(authApplicationService.register(request.countryCode(), request.phone(),
                request.password(), request.displayName(), clientInfo(httpRequest)));
    }

    /**
     * 登录。
     *
     * @param request     请求体
     * @param httpRequest 原始请求
     * @return 登录结果
     */
    @PostMapping("/login")
    @Operation(summary = "手机号密码登录")
    public Result<LoginView> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        return Result.ok(authApplicationService.login(request.countryCode(), request.phone(),
                request.password(), clientInfo(httpRequest)));
    }

    /**
     * 服务人员激活（设置密码并登录）。
     *
     * @param request 请求体
     * @return 登录结果
     */
    @PostMapping("/worker/activate")
    @Operation(summary = "服务人员激活")
    public Result<LoginView> activateWorker(@Valid @RequestBody ActivateWorkerRequest request) {
        return Result.ok(invitationService.activateWorker(request.token(), request.password()));
    }

    /**
     * 退出登录（撤销当前令牌）。
     *
     * @param principal 当前认证主体
     * @return 空结果
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout(@AuthenticationPrincipal AuthenticatedAccount principal) {
        authApplicationService.logout(principal);
        return Result.ok();
    }

    /**
     * 当前账号信息。
     *
     * @param principal 当前认证主体
     * @return 账号视图
     */
    @GetMapping("/me")
    @Operation(summary = "当前账号信息")
    public Result<AccountView> me(@AuthenticationPrincipal AuthenticatedAccount principal) {
        long accountId = currentAccountResolver.requireAccountId();
        String activeRole = principal == null ? null : principal.roleCode();
        return Result.ok(authApplicationService.me(accountId, activeRole));
    }

    /**
     * 切换活动角色。
     *
     * @param request 请求体
     * @return 新的登录结果（新令牌）
     */
    @PostMapping("/switch-role")
    @Operation(summary = "切换活动角色")
    public Result<LoginView> switchRole(@Valid @RequestBody SwitchRoleRequest request) {
        return Result.ok(authApplicationService.switchRole(
                currentAccountResolver.requireAccountId(), request.role()));
    }

    /**
     * 修改密码（成功后所有旧令牌失效）。
     *
     * @param request 请求体
     * @return 空结果
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authApplicationService.changePassword(currentAccountResolver.requireAccountId(),
                request.oldPassword(), request.newPassword());
        return Result.ok();
    }

    /**
     * 提取客户端信息。
     *
     * <p>取 {@code X-Forwarded-For} 的第一个地址（反向代理场景），
     * 否则用直连地址。这里只用于审计，不参与任何权限判断——
     * 该头可以被客户端伪造，绝不能拿来做访问控制。</p>
     */
    private ClientInfo clientInfo(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return new ClientInfo(ip, request.getHeader(HttpHeaders.USER_AGENT));
    }
}
