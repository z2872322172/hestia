package com.housekeeping.platform.aftersales.api;

import com.housekeeping.platform.aftersales.application.AfterSaleApplicationService;
import com.housekeeping.platform.aftersales.application.ReviewApplicationService;
import com.housekeeping.platform.aftersales.application.WorkerQualityService;
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

import java.math.BigDecimal;

/**
 * 运营售后与质量接口（docs/modules/11 第 4.3 节）。
 *
 * <p>受理、流转、形成结论、驳回与关闭工单；隐藏违规评价（保留审计）；
 * 手动触发质量汇总重算。敏感操作均记录操作人。</p>
 */
@RestController
@RequestMapping("/api/v1/operator")
@Validated
@RequiresPersistence
@Tag(name = "运营-售后质量", description = "售后工单处理、评价隐藏、质量汇总重算")
public class OperatorAfterSaleController {

    private final AfterSaleApplicationService afterSaleService;

    private final ReviewApplicationService reviewService;

    private final WorkerQualityService workerQualityService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorAfterSaleController(AfterSaleApplicationService afterSaleService,
                                       ReviewApplicationService reviewService,
                                       WorkerQualityService workerQualityService,
                                       CurrentAccountResolver currentAccountResolver) {
        this.afterSaleService = afterSaleService;
        this.reviewService = reviewService;
        this.workerQualityService = workerQualityService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 工单分页。
     *
     * @param status     状态过滤
     * @param ticketType 类型过滤
     * @param page       页码
     * @param size       条数
     * @return 分页工单
     */
    @GetMapping("/after-sales")
    @Operation(summary = "售后工单列表")
    public Result<PageResult<AfterSaleTicketView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ticketType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(afterSaleService.operatorPage(status, ticketType, page, size));
    }

    /**
     * 工单详情。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @GetMapping("/after-sales/{ticketNo}")
    @Operation(summary = "售后工单详情")
    public Result<AfterSaleTicketView> detail(@PathVariable String ticketNo) {
        return Result.ok(afterSaleService.operatorDetail(ticketNo));
    }

    /**
     * 受理工单：SUBMITTED → PROCESSING。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/accept")
    @Operation(summary = "受理工单")
    public Result<AfterSaleTicketView> accept(@PathVariable String ticketNo) {
        return Result.ok(afterSaleService.accept(ticketNo,
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 转挂起：PROCESSING → PENDING_*。
     *
     * @param ticketNo 工单号
     * @param request  目标状态与原因
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/pending")
    @Operation(summary = "转待反馈", description = "目标为 PENDING_CUSTOMER / PENDING_WORKER / PENDING_REVIEW")
    public Result<AfterSaleTicketView> toPending(@PathVariable String ticketNo,
                                                 @Valid @RequestBody PendingRequest request) {
        return Result.ok(afterSaleService.toPending(ticketNo, request.toStatus(),
                request.reason(), currentAccountResolver.requireAccountId()));
    }

    /**
     * 恢复处理：PENDING_* → PROCESSING。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/processing")
    @Operation(summary = "恢复处理")
    public Result<AfterSaleTicketView> backToProcessing(@PathVariable String ticketNo) {
        return Result.ok(afterSaleService.backToProcessing(ticketNo,
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 形成结论：活动状态 → RESOLVED（退款结论联动支付模块创建退款申请）。
     *
     * @param ticketNo 工单号
     * @param request  结论内容
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/resolve")
    @Operation(summary = "形成处理结论", description = "REFUND 退剩余全额；PARTIAL_REFUND 需指定金额")
    public Result<AfterSaleTicketView> resolve(@PathVariable String ticketNo,
                                               @Valid @RequestBody ResolveRequest request) {
        return Result.ok(afterSaleService.resolve(ticketNo, request.resolutionType(),
                request.resolutionText(), request.responsibility(), request.refundAmount(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 驳回工单：活动状态 → REJECTED。
     *
     * @param ticketNo 工单号
     * @param request  驳回理由
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/reject")
    @Operation(summary = "驳回工单")
    public Result<AfterSaleTicketView> reject(@PathVariable String ticketNo,
                                              @Valid @RequestBody RejectRequest request) {
        return Result.ok(afterSaleService.reject(ticketNo, request.reason(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 关闭工单：RESOLVED / REJECTED → CLOSED。
     *
     * @param ticketNo 工单号
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/close")
    @Operation(summary = "关闭工单")
    public Result<AfterSaleTicketView> close(@PathVariable String ticketNo) {
        return Result.ok(afterSaleService.close(ticketNo,
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 隐藏违规评价（保留原始记录与审计）。
     *
     * @param reviewNo 评价单号
     * @param request  隐藏原因
     * @return 操作结果
     */
    @PostMapping("/reviews/{reviewNo}/hide")
    @Operation(summary = "隐藏违规评价")
    public Result<Void> hideReview(@PathVariable String reviewNo,
                                   @Valid @RequestBody HideReviewRequest request) {
        reviewService.hide(currentAccountResolver.requireAccountId(), reviewNo,
                request.reason());
        return Result.ok(null);
    }

    /**
     * 手动重算人员质量汇总。
     *
     * @param workerId 人员 ID（缺省重算全部已有汇总）
     * @return 操作结果
     */
    @PostMapping("/quality/recalculate")
    @Operation(summary = "重算质量汇总", description = "指定 workerId 重算单人，否则重算全部已有汇总")
    public Result<Void> recalculate(@RequestParam(required = false) Long workerId) {
        if (workerId == null) {
            workerQualityService.recalculateAllExisting();
        } else {
            workerQualityService.recalculate(workerId);
        }
        return Result.ok(null);
    }

    /** 转挂起请求体。 */
    public record PendingRequest(@NotBlank(message = "目标状态不能为空") String toStatus,
                                 String reason) {
    }

    /** 形成结论请求体。 */
    public record ResolveRequest(@NotBlank(message = "处理结果不能为空") String resolutionType,
                                 @NotBlank(message = "结论文本不能为空") String resolutionText,
                                 String responsibility,
                                 BigDecimal refundAmount) {
    }

    /** 驳回请求体。 */
    public record RejectRequest(@NotBlank(message = "驳回理由不能为空") String reason) {
    }

    /** 隐藏评价请求体。 */
    public record HideReviewRequest(@NotBlank(message = "隐藏原因不能为空") String reason) {
    }
}
