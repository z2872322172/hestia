package com.housekeeping.platform.identity.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.identity.api.dto.CreateInvitationRequest;
import com.housekeeping.platform.identity.application.InvitationService;
import com.housekeeping.platform.identity.application.view.InvitationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营端服务人员开户接口。
 *
 * <p>只负责"账号 + WORKER 角色 + 一次性凭证"；
 * 人员档案（技能、证书、服务区域）由人员模块在批次 2 创建，两者不合并。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/workers/invitations")
@RequiresPersistence
@Tag(name = "运营-服务人员", description = "服务人员开户邀请")
public class OperatorInvitationController {

    private final InvitationService invitationService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorInvitationController(InvitationService invitationService,
                                        CurrentAccountResolver currentAccountResolver) {
        this.invitationService = invitationService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 创建服务人员邀请。
     *
     * @param request 请求体
     * @return 邀请结果（含一次性激活凭证）
     */
    @PostMapping
    @Operation(summary = "创建服务人员邀请")
    public Result<InvitationView> create(@Valid @RequestBody CreateInvitationRequest request) {
        return Result.ok(invitationService.createWorkerInvitation(request.countryCode(), request.phone(),
                request.displayName(), currentAccountResolver.requireAccountId()));
    }
}
