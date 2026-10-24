package com.housekeeping.platform.fulfillment.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.fulfillment.application.FulfillmentApplicationService;
import com.housekeeping.platform.fulfillment.application.view.FulfillmentView;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceRecordEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营履约接口（docs/modules/09 第 17.3 节）。
 *
 * <p>运营视角：履约列表与详情、异常处理（写处理人与结论）、强制完成。
 * 证据查看需业务关系，文件本体访问由文件模块校验（docs 第 4.3、9.2 节）。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/fulfillment")
@Validated
@RequiresPersistence
@Tag(name = "运营-履约", description = "履约监控、异常处理与强制完成")
public class OperatorFulfillmentController {

    private final FulfillmentApplicationService fulfillmentService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorFulfillmentController(FulfillmentApplicationService fulfillmentService,
                                         CurrentAccountResolver currentAccountResolver) {
        this.fulfillmentService = fulfillmentService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 履约记录列表分页。
     *
     * @param status 状态过滤，可空
     * @param page   页码
     * @param size   每页大小
     * @return 分页记录列表
     */
    @GetMapping
    @Operation(summary = "履约列表")
    public Result<PageResult<ServiceRecordEntity>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<ServiceRecordEntity> records = fulfillmentService.operatorList(status, page, size);
        long total = fulfillmentService.operatorCount(status);
        return Result.ok(PageResult.of(records, page, size, total));
    }

    /**
     * 履约详情（含检查项、证据、异常聚合）。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @GetMapping("/{orderNo}")
    @Operation(summary = "履约详情")
    public Result<FulfillmentView> detail(@PathVariable String orderNo) {
        return Result.ok(fulfillmentService.operatorDetail(orderNo));
    }

    /**
     * 处理异常：认可（RESOLVED，履约恢复服务）或驳回（REJECTED）。
     *
     * @param orderNo     订单号
     * @param exceptionNo 异常单号
     * @param request     处理结论
     * @return 处理后的异常视图
     */
    @PostMapping("/{orderNo}/exceptions/{exceptionNo}/handle")
    @Operation(summary = "处理异常", description = "处理人、结论与时间随记录写入")
    public Result<FulfillmentView.ExceptionView> handleException(
            @PathVariable String orderNo, @PathVariable String exceptionNo,
            @Valid @RequestBody HandleRequest request) {
        return Result.ok(fulfillmentService.handleException(orderNo, exceptionNo,
                request.accept(), request.resolution(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 强制完成：服务进行中 / 待确认 / 异常状态的订单由运营兜底完结。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @PostMapping("/{orderNo}/force-complete")
    @Operation(summary = "强制完成", description = "confirm_source 记录 OPERATOR")
    public Result<FulfillmentView> forceComplete(@PathVariable String orderNo) {
        return Result.ok(fulfillmentService.forceComplete(orderNo));
    }

    /**
     * 异常处理请求体。
     *
     * @param accept     true=认可恢复服务 / false=驳回
     * @param resolution 处理结论
     */
    public record HandleRequest(@NotNull Boolean accept, @NotBlank String resolution) {
    }
}
