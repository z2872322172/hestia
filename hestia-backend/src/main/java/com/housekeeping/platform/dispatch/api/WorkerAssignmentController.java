package com.housekeeping.platform.dispatch.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.dispatch.application.DispatchApplicationService;
import com.housekeeping.platform.dispatch.application.view.AssignmentView;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 服务人员派单接口（docs/modules/08 第 16.1 节）。
 *
 * <p>人员只能看到定向分配给自己的派单（docs 第 2 节：不提供开放抢单池），
 * 归属由安全上下文决定，不接受 workerId 参数；接单/拒单只对自己的
 * PENDING_ACCEPT 分配生效。</p>
 */
@RestController
@RequestMapping("/api/v1/worker/assignments")
@Validated
@RequiresPersistence
@Tag(name = "服务人员-派单", description = "我的派单列表、详情、接单与拒单")
public class WorkerAssignmentController {

    private final DispatchApplicationService dispatchService;

    private final WorkerProfileService workerProfileService;

    private final CurrentAccountResolver currentAccountResolver;

    public WorkerAssignmentController(DispatchApplicationService dispatchService,
                                      WorkerProfileService workerProfileService,
                                      CurrentAccountResolver currentAccountResolver) {
        this.dispatchService = dispatchService;
        this.workerProfileService = workerProfileService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 我的派单列表（时间倒序）。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页派单列表
     */
    @GetMapping
    @Operation(summary = "我的派单列表")
    public Result<PageResult<AssignmentView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long workerId = currentWorkerId();
        List<AssignmentView> records = dispatchService.workerAssignments(workerId, page, size);
        long total = dispatchService.workerAssignmentCount(workerId);
        return Result.ok(PageResult.of(records, page, size, total));
    }

    /**
     * 派单详情（仅本人）。
     *
     * @param assignmentNo 分配单号
     * @return 派单详情
     */
    @GetMapping("/{assignmentNo}")
    @Operation(summary = "派单详情")
    public Result<AssignmentView> detail(@PathVariable String assignmentNo) {
        return Result.ok(dispatchService.workerAssignment(currentWorkerId(), assignmentNo));
    }

    /**
     * 接单：PENDING_ACCEPT → ACCEPTED，同步任务与订单摘要。
     *
     * @param assignmentNo 分配单号
     * @return 接单后的派单详情
     */
    @PostMapping("/{assignmentNo}/accept")
    @Operation(summary = "接单")
    public Result<AssignmentView> accept(@PathVariable String assignmentNo) {
        return Result.ok(dispatchService.accept(currentWorkerId(), assignmentNo));
    }

    /**
     * 拒单（docs/modules/08 第 2 节：拒单必须填写原因）；
     * 拒单后系统自动尝试下一候选或转人工处理。
     *
     * @param assignmentNo 分配单号
     * @param request      拒单原因
     * @return 拒单后的派单详情
     */
    @PostMapping("/{assignmentNo}/reject")
    @Operation(summary = "拒单", description = "原因码必填；拒单后自动进入下一候选")
    public Result<AssignmentView> reject(@PathVariable String assignmentNo,
                                         @Valid @RequestBody RejectRequest request) {
        return Result.ok(dispatchService.reject(currentWorkerId(), assignmentNo,
                request.reasonCode(), request.reasonText()));
    }

    private long currentWorkerId() {
        return workerProfileService.requireByAccountId(
                currentAccountResolver.requireAccountId()).getId();
    }

    /**
     * 拒单请求体。
     *
     * @param reasonCode 原因码（必填）
     * @param reasonText 原因补充文本（可空）
     */
    public record RejectRequest(@NotBlank String reasonCode, String reasonText) {
    }
}
