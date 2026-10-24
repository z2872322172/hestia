package com.housekeeping.platform.dispatch.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.dispatch.application.DispatchApplicationService;
import com.housekeeping.platform.dispatch.application.view.DispatchCandidateView;
import com.housekeeping.platform.dispatch.application.view.DispatchJobView;
import com.housekeeping.platform.dispatch.persistence.entity.DispatchJobEntity;
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
 * 运营派单接口（docs/modules/08 第 16.2 节）。
 *
 * <p>运营视角：任务队列（待接单/改派/人工处理）、候选评分快照、
 * 分配历史链；手工派单与改派原因必填并写入分配记录审计（docs 第 2、9 节）。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/dispatch")
@Validated
@RequiresPersistence
@Tag(name = "运营-派单", description = "派单任务队列、候选评分与手工派单/改派")
public class OperatorDispatchController {

    private final DispatchApplicationService dispatchService;

    private final CurrentAccountResolver currentAccountResolver;

    public OperatorDispatchController(DispatchApplicationService dispatchService,
                                      CurrentAccountResolver currentAccountResolver) {
        this.dispatchService = dispatchService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 派单任务队列分页。
     *
     * @param status 状态过滤（PENDING_ACCEPT / REASSIGNING / MANUAL_REQUIRED 等），可空
     * @param page   页码
     * @param size   每页大小
     * @return 分页任务列表
     */
    @GetMapping("/jobs")
    @Operation(summary = "派单任务队列")
    public Result<PageResult<DispatchJobEntity>> jobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<DispatchJobEntity> records = dispatchService.operatorJobs(status, page, size);
        long total = dispatchService.operatorJobCount(status);
        return Result.ok(PageResult.of(records, page, size, total));
    }

    /**
     * 任务详情：候选评分快照 + 分配历史链。
     *
     * @param jobNo 任务单号
     * @return 任务视图
     */
    @GetMapping("/jobs/{jobNo}")
    @Operation(summary = "派单任务详情")
    public Result<DispatchJobView> detail(@PathVariable String jobNo) {
        return Result.ok(dispatchService.jobDetail(jobNo));
    }

    /**
     * 任务候选评分快照。
     *
     * @param jobNo 任务单号
     * @return 候选列表（名次升序）
     */
    @GetMapping("/jobs/{jobNo}/candidates")
    @Operation(summary = "任务候选评分")
    public Result<List<DispatchCandidateView>> candidates(@PathVariable String jobNo) {
        return Result.ok(dispatchService.jobDetail(jobNo).candidates());
    }

    /**
     * 手工派单：MANUAL_REQUIRED 任务指定人员（docs/modules/08 第 4.3 节）。
     *
     * @param jobNo   任务单号
     * @param request 指定人员与原因
     * @return 任务视图
     */
    @PostMapping("/jobs/{jobNo}/assign")
    @Operation(summary = "手工派单", description = "针对人工处理队列的任务指定人员")
    public Result<DispatchJobView> assign(@PathVariable String jobNo,
                                          @Valid @RequestBody ManualDispatchRequest request) {
        return Result.ok(dispatchService.assignManual(jobNo, request.workerId(),
                request.reasonText(), currentAccountResolver.requireAccountId()));
    }

    /**
     * 手工改派：更换当前人员（含已接单），原因必填（docs 第 9 节）。
     *
     * @param jobNo   任务单号
     * @param request 新人员与原因
     * @return 任务视图
     */
    @PostMapping("/jobs/{jobNo}/reassign")
    @Operation(summary = "手工改派", description = "自动候选不可用或客户要求更换时使用")
    public Result<DispatchJobView> reassign(@PathVariable String jobNo,
                                            @Valid @RequestBody ManualDispatchRequest request) {
        return Result.ok(dispatchService.reassignManual(jobNo, request.workerId(),
                request.reasonText(), currentAccountResolver.requireAccountId()));
    }

    /**
     * 手工派单/改派请求体。
     *
     * @param workerId   指定人员 ID
     * @param reasonText 操作原因（必填，写审计）
     */
    public record ManualDispatchRequest(@NotNull Long workerId,
                                        @NotBlank String reasonText) {
    }
}
