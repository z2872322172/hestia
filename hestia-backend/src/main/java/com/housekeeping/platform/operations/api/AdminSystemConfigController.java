package com.housekeeping.platform.operations.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.operations.application.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端系统配置接口（docs/modules/13 第 17.3 节）。
 *
 * <p>修改配置必须带 expectedVersion（乐观锁），版本冲突返回
 * SYSTEM_CONFIG_VERSION_CONFLICT；每次成功修改写审计（CONFIG_UPDATE）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/system-configs")
@Validated
@RequiresPersistence
@Tag(name = "管理端-系统配置", description = "非敏感系统配置查询与修改")
public class AdminSystemConfigController {

    private final SystemConfigService configService;

    public AdminSystemConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    /**
     * 配置列表。
     *
     * @return 全部配置
     */
    @GetMapping
    @Operation(summary = "配置列表", description = "按分组 + 配置键排序返回全部非敏感配置")
    public Result<List<SystemConfigService.SystemConfigView>> list() {
        return Result.ok(configService.listAll());
    }

    /**
     * 配置详情。
     *
     * @param configKey 配置键
     * @return 配置视图
     */
    @GetMapping("/{configKey}")
    @Operation(summary = "配置详情")
    public Result<SystemConfigService.SystemConfigView> detail(@PathVariable String configKey) {
        return Result.ok(configService.get(configKey));
    }

    /**
     * 修改配置值。
     *
     * @param configKey 配置键
     * @param request   请求体
     * @return 修改后的配置视图
     */
    @PutMapping("/{configKey}")
    @Operation(summary = "修改配置", description = "expectedVersion 乐观锁；成功后写审计")
    public Result<SystemConfigService.SystemConfigView> update(
            @PathVariable String configKey,
            @RequestBody @jakarta.validation.Valid UpdateConfigRequest request) {
        return Result.ok(configService.update(configKey, request.value(), request.reason(),
                request.expectedVersion()));
    }

    /**
     * 修改配置请求体。
     *
     * @param value           新配置值（原始文本，按类型校验）
     * @param reason          修改原因（写入审计）
     * @param expectedVersion 客户端声明的当前版本
     */
    public record UpdateConfigRequest(
            @NotBlank String value,
            String reason,
            @NotNull Long expectedVersion) {
    }
}
