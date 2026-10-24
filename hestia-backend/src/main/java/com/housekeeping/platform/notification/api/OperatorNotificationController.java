package com.housekeeping.platform.notification.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.notification.application.NotificationTaskService;
import com.housekeeping.platform.notification.application.NotificationTemplateService;
import com.housekeeping.platform.notification.application.view.NotificationTaskView;
import com.housekeeping.platform.notification.application.view.NotificationTemplateView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营通知管理接口（docs/modules/12 第 15.3 节）。
 *
 * <p>失败任务的重试入口：任务进 FAILED 后由运营判断原因
 * （模板缺失就先改模板，瞬时异常就直接重试），重试后立即参与下一轮调度。</p>
 */
@RestController
@RequestMapping("/api/v1/operator")
@Validated
@RequiresPersistence
@Tag(name = "运营-通知管理", description = "任务查询与重试、模板查看与修改")
public class OperatorNotificationController {

    private final NotificationTaskService taskService;

    private final NotificationTemplateService templateService;

    public OperatorNotificationController(NotificationTaskService taskService,
                                          NotificationTemplateService templateService) {
        this.taskService = taskService;
        this.templateService = templateService;
    }

    /**
     * 任务分页。
     *
     * @param status 状态过滤
     * @param page   页码
     * @param size   条数
     * @return 分页任务
     */
    @GetMapping("/notification-tasks")
    @Operation(summary = "通知任务列表")
    public Result<PageResult<NotificationTaskView>> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(taskService.operatorPage(status, page, size));
    }

    /**
     * 人工重试失败任务（FAILED → PENDING）。
     *
     * @param taskNo 任务编号
     * @return 任务视图
     */
    @PostMapping("/notification-tasks/{taskNo}/retry")
    @Operation(summary = "重试失败任务")
    public Result<NotificationTaskView> retry(@PathVariable String taskNo) {
        return Result.ok(taskService.retryTask(taskNo));
    }

    /**
     * 模板列表。
     *
     * @return 模板视图列表
     */
    @GetMapping("/notification-templates")
    @Operation(summary = "通知模板列表")
    public Result<List<NotificationTemplateView>> templates() {
        return Result.ok(templateService.list());
    }

    /**
     * 修改模板内容或启停（内容版本 +1，敏感变量前置拦截）。
     *
     * @param templateCode 模板编码
     * @param request      修改请求
     * @return 修改后的模板视图
     */
    @PutMapping("/notification-templates/{templateCode}")
    @Operation(summary = "修改通知模板")
    public Result<NotificationTemplateView> updateTemplate(
            @PathVariable String templateCode,
            @Valid @RequestBody NotificationTemplateService.TemplateUpdateRequest request) {
        return Result.ok(templateService.update(templateCode, request));
    }
}
