package com.housekeeping.platform.scheduling.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.scheduling.application.AvailabilityQueryService;
import com.housekeeping.platform.scheduling.application.ServiceWindowService;
import com.housekeeping.platform.scheduling.application.view.AvailabilityView;
import com.housekeeping.platform.scheduling.application.view.ServiceWindowView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班公共接口（无需登录）。
 *
 * <p>消费者看到的是"某天某时间窗能否预约"，具体是谁、还剩几个时段都不暴露——
 * 那是运营与派单的事。未上架服务项目在这里表现为"不存在"，
 * 与公共目录保持同一口径，避免用错误码探测未发布商品。</p>
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiresPersistence
@Tag(name = "排班（公共）", description = "可约时段查询与时间窗列表")
public class PublicAvailabilityController {

    private final AvailabilityQueryService availabilityQueryService;

    private final ServiceWindowService windowService;

    public PublicAvailabilityController(AvailabilityQueryService availabilityQueryService,
                                        ServiceWindowService windowService) {
        this.availabilityQueryService = availabilityQueryService;
        this.windowService = windowService;
    }

    /**
     * 查询日期区间内各时间窗的聚合可用性。
     *
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 服务区域 ID，可空
     * @param dateFrom      起始日期（含），不得早于今天
     * @param dateTo        结束日期（含），区间最长 14 天
     * @return 按日期升序的可用性视图
     */
    @GetMapping("/availability")
    @Operation(summary = "查询可约时段")
    public Result<List<AvailabilityView>> availability(
            @RequestParam String serviceItemNo,
            @RequestParam(required = false) Long serviceAreaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(availabilityQueryService.query(serviceItemNo, serviceAreaId,
                dateFrom, dateTo));
    }

    /**
     * 启用中的标准时间窗。
     *
     * @return 时间窗列表
     */
    @GetMapping("/service-windows")
    @Operation(summary = "标准时间窗列表")
    public Result<List<ServiceWindowView>> serviceWindows() {
        return Result.ok(windowService.listActive());
    }
}
