package com.housekeeping.platform.bootstrap.web.vo;

/**
 * 系统心跳响应。
 *
 * @param application 应用名
 * @param profile     当前生效 profile
 * @param serverTime  服务器时间
 * @param traceId     链路标识
 */
public record SystemPingVo(String application, String profile, String serverTime, String traceId) {
}
