package com.housekeeping.platform.booking.api;

import com.housekeeping.platform.booking.application.BookingQueryService;
import com.housekeeping.platform.booking.application.view.BookingDetailView;
import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 运营订单接口（docs/modules/07 第 18.3 节）。
 *
 * <p>运营可查看全部订单并按状态、服务日期过滤；写操作（异常处理、改派）
 * 在后续批次的运营工作台补齐。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/bookings")
@Validated
@RequiresPersistence
@Tag(name = "运营-预约订单", description = "订单监控列表与详情")
public class OperatorBookingController {

    private final BookingQueryService queryService;

    public OperatorBookingController(BookingQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 订单监控列表。
     *
     * @param status          主状态过滤，可空
     * @param appointmentDate 服务日期过滤，可空
     * @param page            页码
     * @param size            每页大小
     * @return 分页列表
     */
    @GetMapping
    @Operation(summary = "订单监控列表")
    public Result<PageResult<BookingSummaryView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appointmentDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(queryService.operatorList(status, appointmentDate, page, size));
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
        return Result.ok(queryService.operatorDetail(orderNo));
    }
}
