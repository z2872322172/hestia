package com.housekeeping.platform.fulfillment.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.fulfillment.application.FulfillmentApplicationService;
import com.housekeeping.platform.fulfillment.application.view.FulfillmentView;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 服务人员履约接口（docs/modules/09 第 17.1 节）。
 *
 * <p>只有当前分配人员可推进履约：归属由安全上下文 + record.worker_id 双重校验，
 * 改派后旧人员所有推进动作一律 404。</p>
 */
@RestController
@RequestMapping("/api/v1/worker/bookings/{orderNo}")
@Validated
@RequiresPersistence
@Tag(name = "服务人员-履约", description = "出发、签到、开始服务、检查项、证据、异常与完成申请")
public class WorkerFulfillmentController {

    private final FulfillmentApplicationService fulfillmentService;

    private final WorkerProfileService workerProfileService;

    private final CurrentAccountResolver currentAccountResolver;

    public WorkerFulfillmentController(FulfillmentApplicationService fulfillmentService,
                                       WorkerProfileService workerProfileService,
                                       CurrentAccountResolver currentAccountResolver) {
        this.fulfillmentService = fulfillmentService;
        this.workerProfileService = workerProfileService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 查看履约进度（仅当前人员）。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @GetMapping("/fulfillment")
    @Operation(summary = "履约详情")
    public Result<FulfillmentView> detail(@PathVariable String orderNo) {
        FulfillmentView view = fulfillmentService.findByOrderNo(orderNo);
        if (view == null || view.workerId() != currentWorkerId()) {
            // 非当前人员的履约与不存在同样按 404 处理
            throw new com.housekeeping.platform.common.exception.BusinessException(
                    com.housekeeping.platform.common.api.ErrorCode.FULFILLMENT_NOT_OWNED);
        }
        return Result.ok(view);
    }

    /**
     * 出发：PENDING → EN_ROUTE。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @PostMapping("/depart")
    @Operation(summary = "出发")
    public Result<FulfillmentView> depart(@PathVariable String orderNo) {
        return Result.ok(fulfillmentService.depart(currentWorkerId(), orderNo));
    }

    /**
     * 签到：EN_ROUTE → CHECKED_IN（坐标可选）。
     *
     * @param orderNo 订单号
     * @param request 签到信息
     * @return 履约聚合视图
     */
    @PostMapping("/check-in")
    @Operation(summary = "签到", description = "必须在预约时间窗允许范围内")
    public Result<FulfillmentView> checkIn(@PathVariable String orderNo,
                                           @Valid @RequestBody CheckInRequest request) {
        return Result.ok(fulfillmentService.checkIn(currentWorkerId(), orderNo,
                request.longitude(), request.latitude(), request.source()));
    }

    /**
     * 开始服务：CHECKED_IN → IN_SERVICE。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @PostMapping("/start")
    @Operation(summary = "开始服务", description = "存在未处理安全异常时被阻断")
    public Result<FulfillmentView> start(@PathVariable String orderNo) {
        return Result.ok(fulfillmentService.start(currentWorkerId(), orderNo));
    }

    /**
     * 完成检查项。
     *
     * @param orderNo 订单号
     * @param itemId  检查项 ID
     * @param request 备注
     * @return 履约聚合视图
     */
    @PutMapping("/checklist/{itemId}")
    @Operation(summary = "完成检查项")
    public Result<FulfillmentView> completeItem(@PathVariable String orderNo,
                                                @PathVariable long itemId,
                                                @RequestBody ChecklistCompleteRequest request) {
        return Result.ok(fulfillmentService.completeChecklistItem(
                currentWorkerId(), orderNo, itemId,
                request == null ? null : request.remark()));
    }

    /**
     * 上传服务证据。
     *
     * @param orderNo 订单号
     * @param request 证据信息
     * @return 履约聚合视图
     */
    @PostMapping("/evidence")
    @Operation(summary = "上传证据", description = "同一文件重复上传幂等")
    public Result<FulfillmentView> uploadEvidence(@PathVariable String orderNo,
                                                  @Valid @RequestBody EvidenceRequest request) {
        return Result.ok(fulfillmentService.uploadEvidence(currentWorkerId(), orderNo,
                request.evidenceType(), request.fileId(), request.checklistItemId(),
                request.description(), currentAccountResolver.requireAccountId()));
    }

    /**
     * 上报现场异常（履约转 EXCEPTION，订单主状态保持）。
     *
     * @param orderNo 订单号
     * @param request 异常信息
     * @return 履约聚合视图
     */
    @PostMapping("/exceptions")
    @Operation(summary = "上报异常")
    public Result<FulfillmentView> reportException(@PathVariable String orderNo,
                                                   @Valid @RequestBody ExceptionReportRequest request) {
        return Result.ok(fulfillmentService.reportException(currentWorkerId(), orderNo,
                request.exceptionType(), request.description(),
                currentAccountResolver.requireAccountId()));
    }

    /**
     * 申请完成：必填检查项与证据校验通过后转 PENDING_CONFIRM。
     *
     * @param orderNo 订单号
     * @param request 服务结果说明
     * @return 履约聚合视图
     */
    @PostMapping("/complete-request")
    @Operation(summary = "申请完成", description = "校验必填检查项与证据完整性")
    public Result<FulfillmentView> requestComplete(@PathVariable String orderNo,
                                                   @RequestBody CompleteRequest request) {
        return Result.ok(fulfillmentService.requestComplete(currentWorkerId(), orderNo,
                request == null ? null : request.serviceResult()));
    }

    private long currentWorkerId() {
        return workerProfileService.requireByAccountId(
                currentAccountResolver.requireAccountId()).getId();
    }

    /**
     * 签到请求体。
     *
     * @param longitude 经度（可空）
     * @param latitude  纬度（可空）
     * @param source    GPS / MANUAL，缺省 GPS
     */
    public record CheckInRequest(BigDecimal longitude, BigDecimal latitude, String source) {
    }

    /**
     * 检查项完成请求体。
     *
     * @param remark 备注（可空）
     */
    public record ChecklistCompleteRequest(String remark) {
    }

    /**
     * 证据上传请求体。
     *
     * @param evidenceType    BEFORE_PHOTO / AFTER_PHOTO / CHECKLIST_PHOTO / DOCUMENT / OTHER
     * @param fileId          文件 ID
     * @param checklistItemId 关联检查项（可空）
     * @param description     说明（可空）
     */
    public record EvidenceRequest(@NotBlank String evidenceType, @NotNull Long fileId,
                                  Long checklistItemId, String description) {
    }

    /**
     * 异常上报请求体。
     *
     * @param exceptionType 异常类型
     * @param description   说明
     */
    public record ExceptionReportRequest(@NotBlank String exceptionType,
                                         @NotBlank String description) {
    }

    /**
     * 完成申请请求体。
     *
     * @param serviceResult 服务结果说明（可空）
     */
    public record CompleteRequest(String serviceResult) {
    }
}
