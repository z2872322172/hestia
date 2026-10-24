package com.housekeeping.platform.operations.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.operations.application.ExportApplicationService;
import com.housekeeping.platform.operations.domain.ExportType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 运营数据导出接口（docs/modules/13 第 17.4 节）。
 *
 * <p>发起 → 列表 → 详情 → 短期签名 URL 下载 → 取消；
 * 首版仅开放订单导出（REPORT_EXPORT_NOT_ALLOWED）。</p>
 */
@RestController
@RequestMapping("/api/v1/operator/exports")
@Validated
@RequiresPersistence
@Tag(name = "运营-数据导出", description = "敏感数据异步导出与下载")
public class OperatorExportController {

    private final ExportApplicationService exportService;

    public OperatorExportController(ExportApplicationService exportService) {
        this.exportService = exportService;
    }

    /**
     * 发起导出请求。
     *
     * @param request 请求体
     * @return 导出任务视图
     */
    @PostMapping
    @Operation(summary = "发起导出", description = "创建异步导出任务，文件生成后可获取短期下载地址")
    public Result<ExportApplicationService.ExportJobView> create(
            @RequestBody @jakarta.validation.Valid CreateExportRequest request) {
        return Result.ok(exportService.create(
                ExportType.fromCode(request.exportType()), request.from(), request.to(),
                request.reason()));
    }

    /**
     * 导出任务列表（运营看自己的，管理员看全部）。
     *
     * @param status 状态过滤（可空）
     * @param page   页码
     * @param size   条数
     * @return 分页任务
     */
    @GetMapping
    @Operation(summary = "导出任务列表")
    public Result<PageResult<ExportApplicationService.ExportJobView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(exportService.page(status, page, size));
    }

    /**
     * 导出任务详情。
     *
     * @param exportNo 导出编号
     * @return 任务视图
     */
    @GetMapping("/{exportNo}")
    @Operation(summary = "导出任务详情")
    public Result<ExportApplicationService.ExportJobView> detail(@PathVariable String exportNo) {
        return Result.ok(exportService.detail(exportNo));
    }

    /**
     * 获取短期下载地址。
     *
     * @param exportNo 导出编号
     * @return 预签名 URL 与有效期
     */
    @PostMapping("/{exportNo}/download-url")
    @Operation(summary = "获取下载地址", description = "私有桶短期签名 URL，每次访问前重新鉴权")
    public Result<ExportApplicationService.DownloadUrlView> downloadUrl(
            @PathVariable String exportNo) {
        return Result.ok(exportService.downloadUrl(exportNo));
    }

    /**
     * 取消待处理的导出任务。
     *
     * @param exportNo 导出编号
     * @return 任务视图
     */
    @PostMapping("/{exportNo}/cancel")
    @Operation(summary = "取消导出", description = "仅 PENDING 状态可取消")
    public Result<ExportApplicationService.ExportJobView> cancel(@PathVariable String exportNo) {
        return Result.ok(exportService.cancel(exportNo));
    }

    /**
     * 发起导出请求体。
     *
     * @param exportType 导出类型（首版仅 ORDERS）
     * @param from       起始日期（含）
     * @param to         结束日期（含）
     * @param reason     导出原因（写入审计，可空）
     */
    public record CreateExportRequest(
            @NotBlank String exportType,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            String reason) {
    }
}
