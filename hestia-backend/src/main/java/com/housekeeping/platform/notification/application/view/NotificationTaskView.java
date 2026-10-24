package com.housekeeping.platform.notification.application.view;

import java.time.LocalDateTime;

/**
 * 通知任务视图（运营端任务列表 / 详情 / 重试）。
 *
 * <p>lastError 与 retryCount 是人工处理的核心信息：
 * 运营看到"模板缺失 / 渲染失败"的原文后决定改模板还是直接重试。</p>
 *
 * @param taskNo            任务编号
 * @param sourceEventId     来源事件 ID
 * @param eventType         事件类型
 * @param templateCode      模板编码
 * @param recipientAccountId 收件账号 ID
 * @param recipientRole     收件角色
 * @param channel           渠道
 * @param status            PENDING / PROCESSING / SENT / FAILED / CANCELED
 * @param retryCount        已失败次数
 * @param maxRetryCount     最大失败次数
 * @param nextRetryAt       下次重试时刻
 * @param lastError         最近失败原因
 * @param sentAt            发送成功时刻
 * @param createdAt         创建时刻
 */
public record NotificationTaskView(String taskNo,
                                   String sourceEventId,
                                   String eventType,
                                   String templateCode,
                                   Long recipientAccountId,
                                   String recipientRole,
                                   String channel,
                                   String status,
                                   Integer retryCount,
                                   Integer maxRetryCount,
                                   LocalDateTime nextRetryAt,
                                   String lastError,
                                   LocalDateTime sentAt,
                                   LocalDateTime createdAt) {
}
