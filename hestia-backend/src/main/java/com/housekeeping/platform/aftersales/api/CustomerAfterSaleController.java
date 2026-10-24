package com.housekeeping.platform.aftersales.api;

import com.housekeeping.platform.aftersales.application.AfterSaleApplicationService;
import com.housekeeping.platform.aftersales.application.view.AfterSaleTicketView;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * 客户售后接口（docs/modules/11 第 4.1 节）。
 */
@RestController
@RequestMapping("/api/v1/customer")
@Validated
@RequiresPersistence
@Tag(name = "客户-售后", description = "提交售后工单、查看进度、回复与确认处理方案")
public class CustomerAfterSaleController {

    private final AfterSaleApplicationService afterSaleService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerAfterSaleController(AfterSaleApplicationService afterSaleService,
                                       CurrentAccountResolver currentAccountResolver) {
        this.afterSaleService = afterSaleService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 提交售后工单。
     *
     * @param orderNo 订单号
     * @param request 工单内容
     * @return 工单视图
     */
    @PostMapping("/bookings/{orderNo}/after-sales")
    @Operation(summary = "提交售后工单")
    public Result<AfterSaleTicketView> create(@PathVariable String orderNo,
                                              @Valid @RequestBody CreateTicketRequest request) {
        return Result.ok(afterSaleService.create(currentAccountResolver.requireAccountId(),
                orderNo, request.ticketType(), request.description(),
                request.priority(), request.fileIds()));
    }

    /**
     * 我的工单列表。
     *
     * @param page 页码
     * @param size 条数
     * @return 分页工单
     */
    @GetMapping("/after-sales")
    @Operation(summary = "我的售后工单")
    public Result<PageResult<AfterSaleTicketView>> myTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(afterSaleService.myTickets(currentAccountResolver.requireAccountId(),
                page, size));
    }

    /**
     * 工单详情（含时间线与附件）。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @GetMapping("/after-sales/{ticketNo}")
    @Operation(summary = "售后工单详情")
    public Result<AfterSaleTicketView> detail(@PathVariable String ticketNo) {
        return Result.ok(afterSaleService.customerTicket(
                currentAccountResolver.requireAccountId(), ticketNo));
    }

    /**
     * 补充回复：PENDING_CUSTOMER → PROCESSING。
     *
     * @param ticketNo 工单号
     * @param request  回复内容
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/reply")
    @Operation(summary = "补充回复", description = "待客户确认状态下继续反馈问题")
    public Result<AfterSaleTicketView> reply(@PathVariable String ticketNo,
                                             @Valid @RequestBody ReplyRequest request) {
        return Result.ok(afterSaleService.customerReply(
                currentAccountResolver.requireAccountId(), ticketNo, request.message()));
    }

    /**
     * 确认处理方案：PENDING_CUSTOMER → CLOSED。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/accept-resolution")
    @Operation(summary = "确认处理方案")
    public Result<AfterSaleTicketView> acceptResolution(@PathVariable String ticketNo) {
        return Result.ok(afterSaleService.acceptResolution(
                currentAccountResolver.requireAccountId(), ticketNo));
    }

    /** 创建工单请求体。 */
    public record CreateTicketRequest(@NotBlank(message = "工单类型不能为空") String ticketType,
                                      @NotBlank(message = "问题描述不能为空") String description,
                                      String priority,
                                      List<Long> fileIds) {
    }

    /** 回复请求体。 */
    public record ReplyRequest(@NotBlank(message = "回复内容不能为空") String message) {
    }
}
