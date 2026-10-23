package com.housekeeping.platform.booking.api;

import com.housekeeping.platform.booking.api.dto.BookingCancelRequest;
import com.housekeeping.platform.booking.api.dto.BookingCreateRequest;
import com.housekeeping.platform.booking.api.dto.BookingRescheduleRequest;
import com.housekeeping.platform.booking.application.BookingApplicationService;
import com.housekeeping.platform.booking.application.BookingQueryService;
import com.housekeeping.platform.booking.application.view.BookingCreatedView;
import com.housekeeping.platform.booking.application.view.BookingDetailView;
import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.catalog.application.view.PricePreviewView;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者订单接口（docs/modules/07 第 18.1 节）。
 *
 * <p>创建接口支持 {@code Idempotency-Key} 头：网络超时后客户端用同一键重试
 * 不会重复下单，直接回放上一次的响应。</p>
 */
@RestController
@RequestMapping("/api/v1/customer/bookings")
@Validated
@RequiresPersistence
@Tag(name = "消费者-预约订单", description = "下单、支付前查询、取消与改期")
public class CustomerBookingController {

    private final BookingApplicationService applicationService;

    private final BookingQueryService queryService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerBookingController(BookingApplicationService applicationService,
                                     BookingQueryService queryService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.applicationService = applicationService;
        this.queryService = queryService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 预览价格。
     *
     * @param request 下单请求（金额构成部分）
     * @return 报价预览
     */
    @PostMapping("/preview")
    @Operation(summary = "预览订单价格")
    public Result<PricePreviewView> preview(@Valid @RequestBody BookingCreateRequest request) {
        return Result.ok(applicationService.preview(request.toCommand()));
    }

    /**
     * 创建订单。
     *
     * @param idempotencyKey 幂等键，可空
     * @param request        下单请求
     * @return 创建结果
     */
    @PostMapping
    @Operation(summary = "创建预约订单")
    public Result<BookingCreatedView> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingCreateRequest request) {
        return Result.ok(applicationService.create(currentAccountResolver.requireAccountId(),
                idempotencyKey, request.toCommand()));
    }

    /**
     * 我的订单列表。
     *
     * @param status 主状态过滤，可空
     * @param page   页码
     * @param size   每页大小
     * @return 分页列表
     */
    @GetMapping
    @Operation(summary = "我的订单列表")
    public Result<PageResult<BookingSummaryView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(queryService.customerList(
                currentAccountResolver.requireAccountId(), page, size, status));
    }

    /**
     * 订单详情。
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    @GetMapping("/{orderNo}")
    @Operation(summary = "订单详情")
    public Result<BookingDetailView> detail(@PathVariable String orderNo) {
        return Result.ok(queryService.customerDetail(
                currentAccountResolver.requireAccountId(), orderNo));
    }

    /**
     * 取消订单。
     *
     * @param orderNo 订单号
     * @param request 取消原因，可为空体
     * @return 空结果
     */
    @PostMapping("/{orderNo}/cancel")
    @Operation(summary = "取消订单")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestBody(required = false) BookingCancelRequest request) {
        String reasonCode = request != null ? request.reasonCode() : null;
        String reasonText = request != null ? request.reasonText() : null;
        applicationService.cancel(currentAccountResolver.requireAccountId(),
                orderNo, reasonCode, reasonText);
        return Result.ok(null);
    }

    /**
     * 改期。
     *
     * @param orderNo 订单号
     * @param request 新日期与时间窗
     * @return 空结果
     */
    @PostMapping("/{orderNo}/reschedule")
    @Operation(summary = "订单改期")
    public Result<Void> reschedule(@PathVariable String orderNo,
                                   @Valid @RequestBody BookingRescheduleRequest request) {
        applicationService.reschedule(currentAccountResolver.requireAccountId(),
                orderNo, request.appointmentDate(), request.serviceWindowCode());
        return Result.ok(null);
    }
}
