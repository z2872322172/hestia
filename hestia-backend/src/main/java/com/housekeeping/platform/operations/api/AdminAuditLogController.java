package com.housekeeping.platform.operations.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.operations.application.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理端审计日志接口（docs/modules/13 第 17.3 节）。
 *
 * <p>审计日志只读，查询受管理端权限控制；audit_log 只追加、
 * 不更新、不删除，因此刻意没有提供任何写接口。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiresPersistence
@Tag(name = "管理端-审计日志", description = "敏感操作审计查询（只读）")
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AdminAuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    /**
     * 审计日志分页。
     *
     * @param actionType        操作类型过滤（可空）
     * @param businessType      业务类型过滤（可空）
     * @param operatorAccountId 操作账号过滤（可空）
     * @param from              操作日期起（含，可空）
     * @param to                操作日期止（含，可空）
     * @param page              页码
     * @param size              条数
     * @return 分页审计日志
     */
    @GetMapping
    @Operation(summary = "审计日志列表", description = "按操作类型 / 业务类型 / 操作账号 / 时间区间过滤")
    public Result<PageResult<AuditLogQueryService.AuditLogView>> page(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Long operatorAccountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(auditLogQueryService.page(actionType, businessType, operatorAccountId,
                from, to, page, size));
    }

    /**
     * 审计日志详情。
     *
     * @param id 主键
     * @return 审计日志视图
     */
    @GetMapping("/{id}")
    @Operation(summary = "审计日志详情")
    public Result<AuditLogQueryService.AuditLogView> detail(@PathVariable long id) {
        return Result.ok(auditLogQueryService.detail(id));
    }
}
