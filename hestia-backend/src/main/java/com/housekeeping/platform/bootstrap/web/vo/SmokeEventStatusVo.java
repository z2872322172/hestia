package com.housekeeping.platform.bootstrap.web.vo;

/**
 * 冒烟事件全链路状态。
 *
 * <p>同时反映"发件箱投递状态"和"消费端幂等记录"，便于一眼判断链路卡在哪一段。</p>
 *
 * @param eventId       事件 ID
 * @param eventType     事件类型
 * @param outboxStatus  发件箱状态：PENDING / SENDING / SENT / FAILED / DEAD
 * @param retryCount    已重试次数
 * @param lastError     最近一次投递失败原因
 * @param sentAt        投递成功时间
 * @param consumed      消费端是否已成功消费
 * @param consumerGroup 消费端组名
 * @param consumedAt    消费时间
 */
public record SmokeEventStatusVo(String eventId, String eventType, String outboxStatus, int retryCount,
                                 String lastError, String sentAt, boolean consumed, String consumerGroup,
                                 String consumedAt) {
}
