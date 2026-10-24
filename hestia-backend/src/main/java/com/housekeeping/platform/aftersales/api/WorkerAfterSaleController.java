package com.housekeeping.platform.aftersales.api;

import com.housekeeping.platform.aftersales.application.AfterSaleApplicationService;
import com.housekeeping.platform.aftersales.application.WorkerQualityService;
import com.housekeeping.platform.aftersales.application.view.AfterSaleTicketView;
import com.housekeeping.platform.aftersales.application.view.WorkerQualitySummaryView;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
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

/**
 * 服务人员评价与售后接口（docs/modules/11 第 4.2 节）。
 *
 * <p>人员可查看与本人相关的评价摘要与工单、对待说明工单提交陈述；
 * 不能删除评价或关闭工单。</p>
 */
@RestController
@RequestMapping("/api/v1/worker")
@Validated
@RequiresPersistence
@Tag(name = "服务人员-评价售后", description = "评价摘要、售后工单查看与陈述")
public class WorkerAfterSaleController {

    private final WorkerQualityService workerQualityService;

    private final AfterSaleApplicationService afterSaleService;

    private final WorkerProfileService workerProfileService;

    private final CurrentAccountResolver currentAccountResolver;

    public WorkerAfterSaleController(WorkerQualityService workerQualityService,
                                     AfterSaleApplicationService afterSaleService,
                                     WorkerProfileService workerProfileService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.workerQualityService = workerQualityService;
        this.afterSaleService = afterSaleService;
        this.workerProfileService = workerProfileService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 评价摘要（质量汇总）。
     *
     * @return 质量汇总视图
     */
    @GetMapping("/reviews/summary")
    @Operation(summary = "我的评价摘要", description = "含平均分、投诉数与派单质量分")
    public Result<WorkerQualitySummaryView> reviewSummary() {
        long workerId = workerProfileService.requireByAccountId(
                currentAccountResolver.requireAccountId()).getId();
        return Result.ok(workerQualityService.summary(workerId));
    }

    /**
     * 与本人相关的售后工单列表。
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
        return Result.ok(afterSaleService.workerTickets(
                currentAccountResolver.requireAccountId(), page, size));
    }

    /**
     * 提交陈述说明：PENDING_WORKER → PROCESSING。
     *
     * @param ticketNo 工单号
     * @param request  陈述内容
     * @return 工单视图
     */
    @PostMapping("/after-sales/{ticketNo}/statement")
    @Operation(summary = "提交陈述说明")
    public Result<AfterSaleTicketView> statement(@PathVariable String ticketNo,
                                                 @Valid @RequestBody StatementRequest request) {
        long workerId = workerProfileService.requireByAccountId(
                currentAccountResolver.requireAccountId()).getId();
        return Result.ok(afterSaleService.workerStatement(workerId, ticketNo,
                request.message()));
    }

    /** 陈述请求体。 */
    public record StatementRequest(@NotBlank(message = "陈述内容不能为空") String message) {
    }
}
