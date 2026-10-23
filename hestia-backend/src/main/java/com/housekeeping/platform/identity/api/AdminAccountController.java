package com.housekeeping.platform.identity.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.pagination.PageQuery;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.api.dto.UpdateAreaScopesRequest;
import com.housekeeping.platform.identity.api.dto.UpdateRolesRequest;
import com.housekeeping.platform.identity.application.AccountAdminService;
import com.housekeeping.platform.identity.application.view.AccountDetailView;
import com.housekeeping.platform.identity.application.view.AccountView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员账号管理接口。
 *
 * <p>路径前缀 {@code /api/v1/admin/**} 在安全配置里要求 ADMIN 角色。
 * 停用、改角色、改区域都会让旧令牌立即失效。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@Validated
@RequiresPersistence
@Tag(name = "管理员-账号", description = "账号查询、停用启用、角色与区域范围")
public class AdminAccountController {

    private final AccountAdminService accountAdminService;

    public AdminAccountController(AccountAdminService accountAdminService) {
        this.accountAdminService = accountAdminService;
    }

    /**
     * 分页查询账号。
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "账号列表")
    public Result<PageResult<AccountView>> list(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) @Size(max = 64) String keyword) {
        PageQuery query = PageQuery.of(page, size);
        return Result.ok(accountAdminService.page(query.getPage(), query.getSize(), keyword));
    }

    /**
     * 账号详情。
     *
     * @param accountNo 账号编号
     * @return 详情
     */
    @GetMapping("/{accountNo}")
    @Operation(summary = "账号详情")
    public Result<AccountDetailView> detail(@PathVariable @Size(max = 32) String accountNo) {
        return Result.ok(accountAdminService.detail(accountNo));
    }

    /**
     * 停用账号。
     *
     * @param accountNo 账号编号
     * @return 空结果
     */
    @PostMapping("/{accountNo}/disable")
    @Operation(summary = "停用账号")
    public Result<Void> disable(@PathVariable @Size(max = 32) String accountNo) {
        accountAdminService.disable(accountNo);
        return Result.ok();
    }

    /**
     * 启用账号。
     *
     * @param accountNo 账号编号
     * @return 空结果
     */
    @PostMapping("/{accountNo}/enable")
    @Operation(summary = "启用账号")
    public Result<Void> enable(@PathVariable @Size(max = 32) String accountNo) {
        accountAdminService.enable(accountNo);
        return Result.ok();
    }

    /**
     * 调整账号角色（整体覆盖）。
     *
     * @param accountNo 账号编号
     * @param request   请求体
     * @return 空结果
     */
    @PutMapping("/{accountNo}/roles")
    @Operation(summary = "调整账号角色")
    public Result<Void> updateRoles(@PathVariable @Size(max = 32) String accountNo,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        accountAdminService.updateRoles(accountNo, request.roles(), null);
        return Result.ok();
    }

    /**
     * 调整运营区域范围（整体覆盖）。
     *
     * @param accountNo 账号编号
     * @param request   请求体
     * @return 空结果
     */
    @PutMapping("/{accountNo}/area-scopes")
    @Operation(summary = "调整运营区域范围")
    public Result<Void> updateAreaScopes(@PathVariable @Size(max = 32) String accountNo,
                                         @Valid @RequestBody UpdateAreaScopesRequest request) {
        accountAdminService.updateAreaScopes(accountNo, request.roleCode(), request.serviceAreaIds());
        return Result.ok();
    }
}
