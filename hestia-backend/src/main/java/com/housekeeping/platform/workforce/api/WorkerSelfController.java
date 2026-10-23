package com.housekeeping.platform.workforce.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.workforce.api.dto.CertificateSubmitRequest;
import com.housekeeping.platform.workforce.api.dto.UpdateWorkerRequest;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import com.housekeeping.platform.workforce.application.view.CertificateView;
import com.housekeeping.platform.workforce.application.view.SkillView;
import com.housekeeping.platform.workforce.application.view.WorkerDetailView;
import com.housekeeping.platform.workforce.application.view.WorkerServiceAreaView;
import com.housekeeping.platform.workforce.application.view.WorkerView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务人员自助接口。
 *
 * <p>所有接口都<b>不接受</b> {@code workerNo} 参数：人员身份由安全上下文解析，
 * 客户端无法通过改参数查看或修改他人档案与证书（docs/modules/05 第 20.3 节）。</p>
 *
 * <p>这里没有任何"审核"或"改资格"的入口：本人能提交材料，但审核结论只能由运营给。
 * 这不是权限没配好，是设计如此——把审核入口放在本人可访问的路径下，
 * 迟早会被当成"自助通过"。</p>
 */
@RestController
@RequestMapping("/api/v1/worker")
@Validated
@RequiresPersistence
@Tag(name = "服务人员-自助", description = "本人档案、技能、证书与服务区域")
public class WorkerSelfController {

    private final WorkerProfileService profileService;

    private final CurrentAccountResolver currentAccountResolver;

    public WorkerSelfController(WorkerProfileService profileService,
                                CurrentAccountResolver currentAccountResolver) {
        this.profileService = profileService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 我的档案。
     *
     * @return 档案摘要
     */
    @GetMapping("/profile")
    @Operation(summary = "我的档案")
    public Result<WorkerView> profile() {
        return Result.ok(profileService.myProfile(currentAccountResolver.requireAccountId()));
    }

    /**
     * 我的完整档案（含技能、证书、区域）。
     *
     * @return 档案详情
     */
    @GetMapping("/profile/detail")
    @Operation(summary = "我的完整档案")
    public Result<WorkerDetailView> detail() {
        return Result.ok(profileService.myDetail(currentAccountResolver.requireAccountId()));
    }

    /**
     * 修改我的基础信息（仅性别与自我介绍）。
     *
     * @param request 修改请求
     * @return 空响应
     */
    @PutMapping("/profile")
    @Operation(summary = "修改我的基础信息")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateWorkerRequest request) {
        profileService.updateMyProfile(currentAccountResolver.requireAccountId(), request.toCommand());
        return Result.ok(null);
    }

    /**
     * 我的技能。
     *
     * @return 技能列表
     */
    @GetMapping("/skills")
    @Operation(summary = "我的技能")
    public Result<List<SkillView>> skills() {
        return Result.ok(profileService.mySkills(currentAccountResolver.requireAccountId()));
    }

    /**
     * 我的服务区域。
     *
     * @return 区域列表
     */
    @GetMapping("/service-areas")
    @Operation(summary = "我的服务区域")
    public Result<List<WorkerServiceAreaView>> serviceAreas() {
        return Result.ok(profileService.myServiceAreas(currentAccountResolver.requireAccountId()));
    }

    /**
     * 我的证书。
     *
     * @return 证书列表
     */
    @GetMapping("/certificates")
    @Operation(summary = "我的证书")
    public Result<List<CertificateView>> certificates() {
        return Result.ok(profileService.myCertificates(currentAccountResolver.requireAccountId()));
    }

    /**
     * 提交证书（提交后为待审核）。
     *
     * @param request 证书信息
     * @return 证书视图
     */
    @PostMapping("/certificates")
    @Operation(summary = "提交证书")
    public Result<CertificateView> submitCertificate(@Valid @RequestBody CertificateSubmitRequest request) {
        return Result.ok(profileService.addCertificate(currentAccountResolver.requireAccountId(),
                request.toCommand()));
    }
}
