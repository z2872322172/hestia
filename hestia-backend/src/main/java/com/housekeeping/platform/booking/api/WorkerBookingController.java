package com.housekeeping.platform.booking.api;

import com.housekeeping.platform.booking.application.BookingQueryService;
import com.housekeeping.platform.booking.application.view.BookingDetailView;
import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务人员任务接口（docs/modules/07 第 18.2 节）。
 *
 * <p>归属由安全上下文决定：人员只能看到当前分派给自己的订单，
 * 不接受 workerNo 参数，杜绝改参数查看他人任务。</p>
 */
@RestController
@RequestMapping("/api/v1/worker/bookings")
@Validated
@RequiresPersistence
@Tag(name = "服务人员-任务订单", description = "我的任务列表与详情")
public class WorkerBookingController {

    private final BookingQueryService queryService;

    private final WorkerProfileService workerProfileService;

    private final CurrentAccountResolver currentAccountResolver;

    public WorkerBookingController(BookingQueryService queryService,
                                   WorkerProfileService workerProfileService,
                                   CurrentAccountResolver currentAccountResolver) {
        this.queryService = queryService;
        this.workerProfileService = workerProfileService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 我的任务列表（按服务开始时间倒序）。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页列表
     */
    @GetMapping
    @Operation(summary = "我的任务列表")
    public Result<PageResult<BookingSummaryView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long workerId = workerProfileService.requireByAccountId(
                currentAccountResolver.requireAccountId()).getId();
        return Result.ok(queryService.workerList(workerId, page, size));
    }

    /**
     * 任务详情（仅分派给本人）。
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    @GetMapping("/{orderNo}")
    @Operation(summary = "任务详情")
    public Result<BookingDetailView> detail(@PathVariable String orderNo) {
        long workerId = workerProfileService.requireByAccountId(
                currentAccountResolver.requireAccountId()).getId();
        return Result.ok(queryService.workerDetail(workerId, orderNo));
    }
}
