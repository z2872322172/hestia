package com.housekeeping.platform.notification.application.view;

import java.time.LocalDateTime;

/**
 * 站内通知视图（收件箱列表 / 详情）。
 *
 * <p>只暴露业务编号与正文，不暴露内部主键：前端用 notificationNo 定位，
 * 他人编号探测由"统一回不存在"挡住。</p>
 *
 * @param notificationNo 通知编号
 * @param title          标题
 * @param content        正文（渲染后，不含敏感明文）
 * @param businessType   业务类型（BOOKING ...）
 * @param businessId     业务编号（如订单号）
 * @param priority       LOW / NORMAL / HIGH / URGENT
 * @param readAt         已读时刻，null 即未读
 * @param createdAt      创建时刻
 */
public record NotificationView(String notificationNo,
                               String title,
                               String content,
                               String businessType,
                               String businessId,
                               String priority,
                               LocalDateTime readAt,
                               LocalDateTime createdAt) {
}
