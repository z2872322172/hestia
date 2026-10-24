package com.housekeeping.platform.fulfillment.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.fulfillment.application.FulfillmentApplicationService;
import com.housekeeping.platform.fulfillment.application.view.FulfillmentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者履约接口（docs/modules/09 第 17.2 节）。
 *
 * <p>客户只能查看本人订单的履约进度并做完成确认；服务人员不能代替客户确认
 * （docs 第 2 节），确认只接受客户本人登录账号。</p>
 */
@RestController
@RequestMapping("/api/v1/customer/bookings/{orderNo}")
@Validated
@RequiresPersistence
@Tag(name = "消费者-履约", description = "履约进度查看与完成确认")
public class CustomerFulfillmentController {

    private final FulfillmentApplicationService fulfillmentService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerFulfillmentController(FulfillmentApplicationService fulfillmentService,
                                         CurrentAccountResolver currentAccountResolver) {
        this.fulfillmentService = fulfillmentService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 查看本人订单履约进度。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @GetMapping("/fulfillment")
    @Operation(summary = "履约进度")
    public Result<FulfillmentView> detail(@PathVariable String orderNo) {
        return Result.ok(fulfillmentService.customerView(
                currentAccountResolver.requireAccountId(), orderNo));
    }

    /**
     * 客户确认完成：履约与订单转 COMPLETED，开放评价与售后。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @PostMapping("/confirm")
    @Operation(summary = "确认完成", description = "仅订单归属客户本人可确认")
    public Result<FulfillmentView> confirm(@PathVariable String orderNo) {
        return Result.ok(fulfillmentService.customerConfirm(
                currentAccountResolver.requireAccountId(), orderNo));
    }
}
