package com.housekeeping.platform.notification.application.view;

import java.time.LocalDateTime;

/**
 * 通知模板视图（运营端查看 / 修改）。
 *
 * @param templateCode    模板编码
 * @param templateName    模板名称
 * @param eventType       事件类型
 * @param audience        受众
 * @param channel         渠道
 * @param titleTemplate   标题模板
 * @param contentTemplate 正文模板
 * @param priority        优先级
 * @param status          ACTIVE / DISABLED
 * @param version         内容版本（每次修改 +1）
 * @param updatedAt       最近修改时刻
 */
public record NotificationTemplateView(String templateCode,
                                       String templateName,
                                       String eventType,
                                       String audience,
                                       String channel,
                                       String titleTemplate,
                                       String contentTemplate,
                                       String priority,
                                       String status,
                                       Long version,
                                       LocalDateTime updatedAt) {
}
