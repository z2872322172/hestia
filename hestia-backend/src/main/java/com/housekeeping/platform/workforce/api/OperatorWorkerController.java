package com.housekeeping.platform.workforce.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.pagination.PageQuery;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.workforce.api.dto.CertificateReviewRequest;
import com.housekeeping.platform.workforce.api.dto.CreateWorkerRequest;
import com.housekeeping.platform.workforce.api.dto.ReplaceServiceAreasRequest;
import com.housekeeping.platform.workforce.api.dto.ReplaceSkillsRequest;
import com.housekeeping.platform.workforce.api.dto.UpdateWorkerRequest;
import com.housekeeping.platform.workforce.application.WorkerAdminService;
import com.housekeeping.platform.workforce.application.WorkerEligibilityService;
import com.housekeeping.platform.workforce.application.view.CertificateView;
import com.housekeeping.platform.workforce.application.view.CreatedWorkerView;
import com.housekeeping.platform.workforce.application.view.EligibilityView;
import com.housekeeping.platform.workforce.application.view.WorkerDetailView;
import com.housekeeping.platform.workforce.application.view.WorkerView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 人员管理接口（运营端）。
 *
 * <p>暂停、恢复、离职三个动作都会联动身份模块的 WORKER 角色；
 * 只改人员表的话，被暂停的人用旧令牌仍能访问 {@code /api/v1/worker/**}。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/workers")
@Validated
@RequiresPersistence
@Tag(name = "运营-人员管理", description = "人员档案、审核、技能、证书、区域与派单资格")
public class OperatorWorkerController {

    private final WorkerAdminService adminService;

    private final WorkerEligibilityService eligibilityService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorWorkerController(WorkerAdminService adminService,
                                    WorkerEligibilityService eligibilityService,
                                    CurrentAccountResolver currentAccountResolver) {
        this.adminService = adminService;
        this.eligibilityService = eligibilityService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 人员分页。
     *
     * @param page        页码
     * @param size        单页条数
     * @param keyword     姓名或编号关键字
     * @param workStatus  工作状态
     * @param auditStatus 审核状态
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "人员分页")
    public Result<PageResult<WorkerView>> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String workStatus,
            @RequestParam(required = false) String auditStatus) {
        PageQuery query = PageQuery.of(page, size);
        return Result.ok(adminService.page(query.getPage(), query.getSize(), keyword,
                workStatus, auditStatus));
    }

    /**
     * 创建人员档案并开户。
     *
     * @param request 创建请求
     * @return 创建结果（含一次性激活凭证）
     */
    @PostMapping
    @Operation(summary = "创建人员档案并开户")
    public Result<CreatedWorkerView> create(@Valid @RequestBody CreateWorkerRequest request) {
        return Result.ok(adminService.create(request.toCommand(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 人员详情。
     *
     * @param workerNo 人员编号
     * @return 详情
     */
    @GetMapping("/{workerNo}")
    @Operation(summary = "人员详情")
    public Result<WorkerDetailView> detail(@PathVariable String workerNo) {
        return Result.ok(adminService.detail(workerNo));
    }

    /**
     * 修改人员档案。
     *
     * @param workerNo 人员编号
     * @param request  修改请求
     * @return 空响应
     */
    @PutMapping("/{workerNo}")
    @Operation(summary = "修改人员档案")
    public Result<Void> update(@PathVariable String workerNo,
                               @Valid @RequestBody UpdateWorkerRequest request) {
        adminService.update(workerNo, request.toCommand(), currentAccountResolver.requireAccountId());
        return Result.ok(null);
    }

    /**
     * 审核通过并办理入职。
     *
     * @param workerNo 人员编号
     * @return 最新详情
     */
    @PostMapping("/{workerNo}/approve")
    @Operation(summary = "审核通过并入职")
    public Result<WorkerDetailView> approve(@PathVariable String workerNo) {
        return Result.ok(adminService.approve(workerNo, currentAccountResolver.requireAccountId()));
    }

    /**
     * 暂停接单。
     *
     * @param workerNo 人员编号
     * @return 空响应
     */
    @PostMapping("/{workerNo}/suspend")
    @Operation(summary = "暂停接单")
    public Result<Void> suspend(@PathVariable String workerNo) {
        adminService.suspend(workerNo, currentAccountResolver.requireAccountId());
        return Result.ok(null);
    }

    /**
     * 恢复接单。
     *
     * @param workerNo 人员编号
     * @return 空响应
     */
    @PostMapping("/{workerNo}/reactivate")
    @Operation(summary = "恢复接单")
    public Result<Void> reactivate(@PathVariable String workerNo) {
        adminService.reactivate(workerNo, currentAccountResolver.requireAccountId());
        return Result.ok(null);
    }

    /**
     * 办理离职。
     *
     * @param workerNo 人员编号
     * @return 空响应
     */
    @PostMapping("/{workerNo}/leave")
    @Operation(summary = "办理离职")
    public Result<Void> leave(@PathVariable String workerNo) {
        adminService.leave(workerNo, currentAccountResolver.requireAccountId());
        return Result.ok(null);
    }

    /**
     * 整体替换技能。
     *
     * @param workerNo 人员编号
     * @param request  技能列表
     * @return 空响应
     */
    @PutMapping("/{workerNo}/skills")
    @Operation(summary = "替换人员技能")
    public Result<Void> replaceSkills(@PathVariable String workerNo,
                                      @Valid @RequestBody ReplaceSkillsRequest request) {
        adminService.replaceSkills(workerNo,
                request.skills() == null ? null : request.skills().stream().map(s -> s.toCommand()).toList(),
                currentAccountResolver.requireAccountId());
        return Result.ok(null);
    }

    /**
     * 整体替换服务区域。
     *
     * @param workerNo 人员编号
     * @param request  区域列表
     * @return 空响应
     */
    @PutMapping("/{workerNo}/service-areas")
    @Operation(summary = "替换人员服务区域")
    public Result<Void> replaceServiceAreas(@PathVariable String workerNo,
                                            @Valid @RequestBody ReplaceServiceAreasRequest request) {
        adminService.replaceServiceAreas(workerNo, request.serviceAreaIds(),
                currentAccountResolver.requireAccountId());
        return Result.ok(null);
    }

    /**
     * 审核证书。
     *
     * @param workerNo      人员编号
     * @param certificateId 证书 ID
     * @param request       审核请求
     * @return 审核后的证书
     */
    @PostMapping("/{workerNo}/certificates/{certificateId}/review")
    @Operation(summary = "审核证书")
    public Result<CertificateView> reviewCertificate(@PathVariable String workerNo,
                                                     @PathVariable long certificateId,
                                                     @Valid @RequestBody CertificateReviewRequest request) {
        return Result.ok(adminService.reviewCertificate(workerNo, certificateId, request.toCommand(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 查询可派单资格。
     *
     * <p>这是"未审核人员不能派单"的可验证入口：返回 {@code eligible=false} 与完整原因列表，
     * 而不是只给一个布尔值让运营去猜。</p>
     *
     * @param workerNo      人员编号
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 服务区域 ID，可空表示不校验区域
     * @return 资格视图
     */
    @GetMapping("/{workerNo}/eligibility")
    @Operation(summary = "查询可派单资格")
    public Result<EligibilityView> eligibility(@PathVariable String workerNo,
                                               @RequestParam String serviceItemNo,
                                               @RequestParam(required = false) Long serviceAreaId) {
        return Result.ok(eligibilityService.assess(workerNo, serviceItemNo, serviceAreaId));
    }
}
