package com.housekeeping.platform.workforce.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.workforce.api.dto.QualificationRequirementRequest;
import com.housekeeping.platform.workforce.application.QualificationRequirementService;
import com.housekeeping.platform.workforce.application.view.QualificationRequirementView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务项目资质要求接口（运营端）。
 *
 * <p>路径挂在服务项目下而不是人员下：要求是"这类活需要什么证"，
 * 属于服务项目的属性，只是由人员模块负责维护与使用。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/service-items")
@Validated
@RequiresPersistence
@Tag(name = "运营-资质要求", description = "服务项目所需证书类型")
public class OperatorQualificationController {

    private final QualificationRequirementService requirementService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorQualificationController(QualificationRequirementService requirementService,
                                           CurrentAccountResolver currentAccountResolver) {
        this.requirementService = requirementService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 查询服务项目的资质要求。
     *
     * @param itemNo 服务项目编号
     * @return 要求列表
     */
    @GetMapping("/{itemNo}/qualification-requirements")
    @Operation(summary = "查询服务项目资质要求")
    public Result<List<QualificationRequirementView>> list(@PathVariable String itemNo) {
        return Result.ok(requirementService.list(itemNo));
    }

    /**
     * 整体替换服务项目的资质要求。
     *
     * @param itemNo  服务项目编号
     * @param request 要求列表
     * @return 替换后的要求列表
     */
    @PutMapping("/{itemNo}/qualification-requirements")
    @Operation(summary = "替换服务项目资质要求")
    public Result<List<QualificationRequirementView>> replace(
            @PathVariable String itemNo,
            @Valid @RequestBody QualificationRequirementRequest request) {
        List<QualificationRequirementView> views = request.requirements() == null
                ? List.of()
                : request.requirements().stream().map(QualificationRequirementRequest.Item::toView).toList();
        return Result.ok(requirementService.replace(itemNo, views,
                currentAccountResolver.requireAccountId()));
    }
}
