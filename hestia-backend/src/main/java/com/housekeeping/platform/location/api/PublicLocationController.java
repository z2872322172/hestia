package com.housekeeping.platform.location.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.location.api.dto.ServiceAreaMatchRequest;
import com.housekeeping.platform.location.api.dto.ServiceAreaMatchResponse;
import com.housekeeping.platform.location.application.ServiceAreaAdminService;
import com.housekeeping.platform.location.application.ServiceAreaMatchService;
import com.housekeeping.platform.location.application.view.ServiceAreaPublicView;
import com.housekeeping.platform.location.domain.ServiceAreaMatchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 位置公共接口（无需登录）。
 *
 * <p>只暴露"有没有服务"和区域名称，不暴露覆盖明细与优先级。
 * 公共匹配接口是外部可打的目标，生产环境应叠加按 IP 的限流（批次 6 统一接入）。</p>
 */
@RestController
@RequestMapping("/api/v1/public")
@Validated
@RequiresPersistence
@Tag(name = "位置-公共", description = "城市服务区域查询与地址范围校验")
public class PublicLocationController {

    private final ServiceAreaAdminService serviceAreaAdminService;

    private final ServiceAreaMatchService serviceAreaMatchService;

    public PublicLocationController(ServiceAreaAdminService serviceAreaAdminService,
                                    ServiceAreaMatchService serviceAreaMatchService) {
        this.serviceAreaAdminService = serviceAreaAdminService;
        this.serviceAreaMatchService = serviceAreaMatchService;
    }

    /**
     * 查询城市公开服务区域。
     *
     * @param cityCode 城市行政区编码
     * @return 服务区域列表
     */
    @GetMapping("/service-areas")
    @Operation(summary = "查询城市服务区域")
    public Result<List<ServiceAreaPublicView>> listServiceAreas(
            @RequestParam @NotBlank(message = "城市编码不能为空")
            @Size(max = 12, message = "城市编码过长") String cityCode) {
        return Result.ok(serviceAreaAdminService.listPublic(cityCode));
    }

    /**
     * 校验地址是否在服务范围内。
     *
     * <p>该接口不落库、不加锁，只回答"这个地址现在能不能服务"。
     * 真正的时段与人员可用性由排班、订单模块校验。</p>
     *
     * @param request 匹配请求
     * @return 匹配结果
     */
    @PostMapping("/service-area-match")
    @Operation(summary = "地址服务范围校验")
    public Result<ServiceAreaMatchResponse> matchServiceArea(@Valid @RequestBody ServiceAreaMatchRequest request) {
        ServiceAreaMatchQuery query = new ServiceAreaMatchQuery(request.cityCode(), request.districtCode(),
                request.streetCode(), request.longitude(), request.latitude());
        return Result.ok(ServiceAreaMatchResponse.of(serviceAreaMatchService.match(query)));
    }
}
