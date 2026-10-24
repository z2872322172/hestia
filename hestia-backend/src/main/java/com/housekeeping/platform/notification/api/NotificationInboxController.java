package com.housekeeping.platform.notification.api;

import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.notification.application.NotificationQueryService;
import com.housekeeping.platform.notification.application.view.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内消息收件箱接口（docs/modules/12 第 15.1 节）。
 *
 * <p>路径不带角色前缀：客户、人员、运营都通过登录态访问自己的收件箱，
 * 数据范围由应用服务按当前账号 ID 收敛，越权回"不存在"。</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiresPersistence
@Tag(name = "站内消息", description = "收件箱、未读数、已读标记")
public class NotificationInboxController {

    private final NotificationQueryService notificationQueryService;

    private final CurrentAccountResolver currentAccountResolver;

    public NotificationInboxController(NotificationQueryService notificationQueryService,
                                       CurrentAccountResolver currentAccountResolver) {
        this.notificationQueryService = notificationQueryService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 收件箱分页。
     *
     * @param onlyUnread 只看未读
     * @param page       页码
     * @param size       条数
     * @return 分页通知
     */
    @GetMapping
    @Operation(summary = "收件箱列表")
    public Result<PageResult<NotificationView>> inbox(
            @RequestParam(name = "onlyUnread", required = false, defaultValue = "false") boolean onlyUnread,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(notificationQueryService.inbox(
                currentAccountResolver.requireAccountId(), onlyUnread, page, size));
    }

    /**
     * 未读数量。
     *
     * @return 未读数
     */
    @GetMapping("/unread-count")
    @Operation(summary = "未读数量")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("unread",
                notificationQueryService.unreadCount(currentAccountResolver.requireAccountId())));
    }

    /**
     * 标记单条已读（重复标记幂等）。
     *
     * @param notificationNo 通知编号
     * @return 空响应
     */
    @PutMapping("/{notificationNo}/read")
    @Operation(summary = "标记已读")
    public Result<Void> markRead(@PathVariable String notificationNo) {
        notificationQueryService.markRead(currentAccountResolver.requireAccountId(), notificationNo);
        return Result.ok(null);
    }

    /**
     * 全部标记已读。
     *
     * @return 本次标记条数
     */
    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public Result<Map<String, Long>> markAllRead() {
        return Result.ok(Map.of("marked",
                notificationQueryService.markAllRead(currentAccountResolver.requireAccountId())));
    }
}
