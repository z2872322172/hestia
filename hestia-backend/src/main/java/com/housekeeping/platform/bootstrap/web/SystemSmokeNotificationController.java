package com.housekeeping.platform.bootstrap.web;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.notification.application.NotificationDispatchJob;
import com.housekeeping.platform.notification.application.NotificationTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通知链路冒烟接口。
 *
 * <p>只在 {@code app.security.smoke-api-enabled=true} 的 profile 下可访问，
 * 生产环境由安全配置统一 denyAll。两个端点把链路拆成两步验证：</p>
 * <ul>
 *   <li>{@code ingest}：模拟"事件已确认消费"，直接走任务创建（与 MQ 消费者同一入口），
 *       让本地无 RocketMQ 时也能验证收件人解析与去重；</li>
 *   <li>{@code dispatch}：触发一轮发送扫描（与定时任务同一入口），可注入模拟时刻
 *       验证重试退避（如把 asOf 设到 next_retry_at 之后）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/system/smoke/notification")
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "系统冒烟-通知", description = "任务注入与发送扫描触发")
public class SystemSmokeNotificationController {

    private final NotificationTaskService taskService;

    private final NotificationDispatchJob dispatchJob;

    public SystemSmokeNotificationController(NotificationTaskService taskService,
                                             NotificationDispatchJob dispatchJob) {
        this.taskService = taskService;
        this.dispatchJob = dispatchJob;
    }

    /**
     * 注入一条事件（跳过 MQ，直接创建通知任务）。
     *
     * @param request 事件类型、事件 ID 与载荷
     * @return 新建的任务编号列表
     */
    @PostMapping("/ingest")
    @Operation(summary = "注入通知事件", description = "模拟事件消费，创建通知任务；重复注入命中去重键返回空列表")
    public Result<Map<String, List<String>>> ingest(@RequestBody IngestRequest request) {
        List<String> taskNos = taskService.createTasksForEvent(request.eventType(),
                request.sourceEventId(), request.orderNo(), request.payload());
        return Result.ok(Map.of("taskNos", taskNos));
    }

    /**
     * 触发一轮通知发送扫描。
     *
     * @param asOf 可选模拟时刻（ISO 格式），用于验证重试退避
     * @return 本轮处理的任务数
     */
    @PostMapping("/dispatch")
    @Operation(summary = "触发通知发送扫描", description = "执行 NotificationDispatchJob.runOnce，可注入模拟时钟")
    public Result<Map<String, Integer>> dispatch(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        return Result.ok(Map.of("handled",
                asOf == null ? dispatchJob.runOnce() : dispatchJob.runOnce(asOf)));
    }

    /**
     * 注入请求：eventType 必填，sourceEventId 缺省用 "SMOKE-" + orderNo，
     * orderNo 作为 aggregateId 只用于日志与 businessId。
     *
     * @param eventType     事件类型
     * @param sourceEventId 来源事件 ID
     * @param orderNo       订单号（聚合 ID）
     * @param payload       事件载荷
     */
    public record IngestRequest(String eventType,
                                String sourceEventId,
                                String orderNo,
                                Map<String, Object> payload) {
    }
}
