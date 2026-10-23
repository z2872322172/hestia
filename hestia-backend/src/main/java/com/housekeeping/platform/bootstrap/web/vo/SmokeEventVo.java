package com.housekeeping.platform.bootstrap.web.vo;

/**
 * 冒烟事件提交结果。
 *
 * @param eventId 事件 ID，用于后续查询投递与消费状态
 * @param status  发件箱初始状态，固定 {@code PENDING}
 * @param topic   目标 Topic
 */
public record SmokeEventVo(String eventId, String status, String topic) {
}
