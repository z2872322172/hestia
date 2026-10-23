package com.housekeeping.platform.location.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.location.api.dto.AddCoverageRequest;
import com.housekeeping.platform.location.api.dto.CreateServiceAreaRequest;
import com.housekeeping.platform.location.api.dto.UpdateServiceAreaRequest;
import com.housekeeping.platform.location.application.ServiceAreaAdminService;
import com.housekeeping.platform.location.application.command.CreateServiceAreaCommand;
import com.housekeeping.platform.location.application.command.UpdateServiceAreaCommand;
import com.housekeeping.platform.location.application.view.CoverageView;
import com.housekeeping.platform.location.application.view.ServiceAreaView;
import com.housekeeping.platform.location.domain.ServiceAreaStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.util.List;

/**
 * 运营端服务区域维护接口。
 *
 * <p>路径前缀 {@code /api/v1/operator/**} 在安全配置里要求 OPERATOR 或 ADMIN 角色，
 * 数据范围（只能看自己区域的）由后续批次的区域过滤统一处理。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/service-areas")
@Validated
@RequiresPersistence
@Tag(name = "运营-服务区域", description = "服务区域与覆盖范围维护")
public class OperatorServiceAreaController {

    private final ServiceAreaAdminService serviceAreaAdminService;

    public OperatorServiceAreaController(ServiceAreaAdminService serviceAreaAdminService) {
        this.serviceAreaAdminService = serviceAreaAdminService;
    }

    /**
     * 查询城市下的服务区域（含草稿与停用）。
     *
     * @param cityCode 城市行政区编码
     * @return 服务区域列表
     */
    @GetMapping
    @Operation(summary = "服务区域列表")
    public Result<List<ServiceAreaView>> list(
            @RequestParam @NotBlank(message = "城市编码不能为空")
            @Size(max = 12, message = "城市编码过长") String cityCode) {
        return Result.ok(serviceAreaAdminService.list(cityCode));
    }

    /**
     * 服务区域详情。
     *
     * @param areaNo 编号
     * @return 详情
     */
    @GetMapping("/{areaNo}")
    @Operation(summary = "服务区域详情")
    public Result<ServiceAreaView> detail(@PathVariable @Size(max = 32) String areaNo) {
        return Result.ok(serviceAreaAdminService.detail(areaNo));
    }

    /**
     * 创建服务区域（初始为 DRAFT）。
     *
     * @param request 请求体
     * @return 新建结果
     */
    @PostMapping
    @Operation(summary = "创建服务区域")
    public Result<ServiceAreaView> create(@Valid @RequestBody CreateServiceAreaRequest request) {
        CreateServiceAreaCommand command = new CreateServiceAreaCommand(request.areaName(),
                request.description(), request.cityCode(), request.areaType(), request.priority(),
                request.effectiveFrom(), request.effectiveTo());
        return Result.ok(serviceAreaAdminService.create(command));
    }

    /**
     * 修改服务区域。
     *
     * @param areaNo  编号
     * @param request 请求体（含版本号）
     * @return 修改结果
     */
    @PutMapping("/{areaNo}")
    @Operation(summary = "修改服务区域")
    public Result<ServiceAreaView> update(@PathVariable @Size(max = 32) String areaNo,
                                          @Valid @RequestBody UpdateServiceAreaRequest request) {
        UpdateServiceAreaCommand command = new UpdateServiceAreaCommand(request.areaName(),
                request.description(), request.priority(), request.effectiveFrom(),
                request.effectiveTo(), request.version());
        return Result.ok(serviceAreaAdminService.update(areaNo, command));
    }

    /**
     * 启用服务区域。
     *
     * @param areaNo 编号
     * @return 变更结果
     */
    @PostMapping("/{areaNo}/activate")
    @Operation(summary = "启用服务区域")
    public Result<ServiceAreaView> activate(@PathVariable @Size(max = 32) String areaNo) {
        return Result.ok(serviceAreaAdminService.changeStatus(areaNo, ServiceAreaStatus.ACTIVE));
    }

    /**
     * 停用服务区域。
     *
     * @param areaNo 编号
     * @return 变更结果
     */
    @PostMapping("/{areaNo}/deactivate")
    @Operation(summary = "停用服务区域")
    public Result<ServiceAreaView> deactivate(@PathVariable @Size(max = 32) String areaNo) {
        return Result.ok(serviceAreaAdminService.changeStatus(areaNo, ServiceAreaStatus.INACTIVE));
    }

    /**
     * 归档服务区域（不可恢复）。
     *
     * @param areaNo 编号
     * @return 变更结果
     */
    @PostMapping("/{areaNo}/archive")
    @Operation(summary = "归档服务区域")
    public Result<ServiceAreaView> archive(@PathVariable @Size(max = 32) String areaNo) {
        return Result.ok(serviceAreaAdminService.changeStatus(areaNo, ServiceAreaStatus.ARCHIVED));
    }

    /**
     * 查询覆盖范围。
     *
     * @param areaNo 编号
     * @return 覆盖列表
     */
    @GetMapping("/{areaNo}/coverages")
    @Operation(summary = "覆盖范围列表")
    public Result<List<CoverageView>> listCoverages(@PathVariable @Size(max = 32) String areaNo) {
        return Result.ok(serviceAreaAdminService.listCoverages(areaNo));
    }

    /**
     * 新增覆盖范围。
     *
     * @param areaNo  编号
     * @param request 请求体
     * @return 新建的覆盖记录
     */
    @PostMapping("/{areaNo}/coverages")
    @Operation(summary = "新增覆盖范围")
    public Result<CoverageView> addCoverage(@PathVariable @Size(max = 32) String areaNo,
                                            @Valid @RequestBody AddCoverageRequest request) {
        return Result.ok(serviceAreaAdminService.addCoverage(areaNo, request.regionCode()));
    }

    /**
     * 停用覆盖范围。
     *
     * @param areaNo     编号
     * @param coverageId 覆盖记录 ID
     * @return 空结果
     */
    @PostMapping("/{areaNo}/coverages/{coverageId}/disable")
    @Operation(summary = "停用覆盖范围")
    public Result<Void> disableCoverage(@PathVariable @Size(max = 32) String areaNo,
                                        @PathVariable long coverageId) {
        serviceAreaAdminService.disableCoverage(areaNo, coverageId);
        return Result.ok();
    }
}
