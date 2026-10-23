package com.housekeeping.platform.bootstrap.web;

import com.housekeeping.platform.bootstrap.web.vo.GeoSmokeVo;
import com.housekeeping.platform.bootstrap.web.vo.IntegrationStatusVo;
import com.housekeeping.platform.bootstrap.web.vo.SystemPingVo;
import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.infrastructure.map.GeocodeResult;
import com.housekeeping.platform.infrastructure.map.MapQueryService;
import com.housekeeping.platform.infrastructure.observability.IntegrationStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 系统自检接口。
 *
 * <p>这些接口只证明"应用起来了、依赖连上了、地图能切换"，不返回任何业务数据。
 * 生产环境只保留 ping 与 integrations（smoke/geo 随 smoke 接口一并关闭）。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "系统集成", description = "技术骨架自检接口，不属于业务 API")
public class SystemController {

    private final Environment environment;

    private final IntegrationStatusService integrationStatusService;

    private final MapQueryService mapQueryService;

    public SystemController(Environment environment,
                            IntegrationStatusService integrationStatusService,
                            MapQueryService mapQueryService) {
        this.environment = environment;
        this.integrationStatusService = integrationStatusService;
        this.mapQueryService = mapQueryService;
    }

    /**
     * 应用存活检查。
     *
     * @return 应用名、profile、服务器时间与 traceId
     */
    @GetMapping("/ping")
    @Operation(summary = "应用心跳", description = "返回应用名、当前 profile 与 traceId，用于确认应用已启动")
    public Result<SystemPingVo> ping() {
        String applicationName = environment.getProperty("spring.application.name", "hestia-backend");
        return Result.ok(new SystemPingVo(
                applicationName,
                activeProfile(),
                LocalDateTime.now().toString(),
                RequestContext.getTraceId()));
    }

    /**
     * 集成组件状态。
     *
     * @return 各组件健康状态与当前地图 Provider
     */
    @GetMapping("/integrations")
    @Operation(summary = "集成状态", description = "聚合 Actuator 健康检查结果，展示 db/redis/minio/rocketMq/map 状态")
    public Result<IntegrationStatusVo> integrations() {
        return Result.ok(new IntegrationStatusVo(
                integrationStatusService.componentStatuses(),
                mapQueryService.providerName()));
    }

    /**
     * 地理编码冒烟。
     *
     * @param address 待解析地址
     * @return 坐标与匹配级别
     */
    @GetMapping("/smoke/geo")
    @Operation(summary = "地理编码冒烟", description = "验证 Mock / 高德 Provider 可切换；生产环境关闭")
    public Result<GeoSmokeVo> geo(@RequestParam("address")
                                  @NotBlank(message = "不能为空")
                                  @Size(max = 200, message = "长度不能超过 200") String address) {
        GeocodeResult geocodeResult = mapQueryService.geocode(address);
        return Result.ok(new GeoSmokeVo(
                geocodeResult.provider(),
                geocodeResult.formattedAddress(),
                geocodeResult.point().longitude(),
                geocodeResult.point().latitude(),
                geocodeResult.level()));
    }

    /**
     * 当前生效 profile；未显式激活时返回 {@code default}。
     */
    private String activeProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return activeProfiles[0];
        }
        String[] defaultProfiles = environment.getDefaultProfiles();
        return defaultProfiles.length > 0 ? defaultProfiles[0] : "default";
    }
}
