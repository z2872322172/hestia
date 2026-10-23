package com.housekeeping.platform.scheduling.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.scheduling.api.dto.ServiceWindowRequest;
import com.housekeeping.platform.scheduling.api.dto.ServiceWindowUpdateRequest;
import com.housekeeping.platform.scheduling.application.ServiceWindowService;
import com.housekeeping.platform.scheduling.application.view.ServiceWindowView;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标准服务时间窗维护接口（运营端）。
 *
 * <p>时间窗是公司级配置：数量少、变化低频，但所有排班与可约时段都从它派生。
 * 修改起止时间只影响后续生成的时段，存量时段不回溯
 * （docs/modules/06 第 19.2 节），所以界面必须展示版本号供并发控制。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/service-windows")
@Validated
@RequiresPersistence
@Tag(name = "运营-服务时间窗", description = "标准服务时间窗维护")
public class OperatorServiceWindowController {

    private final ServiceWindowService windowService;

    public OperatorServiceWindowController(ServiceWindowService windowService) {
        this.windowService = windowService;
    }

    /**
     * 时间窗列表（含停用）。
     *
     * @return 时间窗列表
     */
    @GetMapping
    @Operation(summary = "时间窗列表")
    public Result<List<ServiceWindowView>> list() {
        return Result.ok(windowService.listForOperator());
    }

    /**
     * 新建时间窗。
     *
     * @param request 创建请求
     * @return 时间窗视图
     */
    @PostMapping
    @Operation(summary = "新建时间窗")
    public Result<ServiceWindowView> create(@Valid @RequestBody ServiceWindowRequest request) {
        return Result.ok(windowService.create(request.toCommand()));
    }

    /**
     * 修改时间窗。
     *
     * @param windowId 时间窗 ID
     * @param request  修改请求
     * @return 时间窗视图
     */
    @PutMapping("/{windowId}")
    @Operation(summary = "修改时间窗")
    public Result<ServiceWindowView> update(@PathVariable long windowId,
                                            @Valid @RequestBody ServiceWindowUpdateRequest request) {
        return Result.ok(windowService.update(windowId, request.toCommand()));
    }
}
