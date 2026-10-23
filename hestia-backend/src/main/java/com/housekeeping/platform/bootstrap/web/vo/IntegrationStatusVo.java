package com.housekeeping.platform.bootstrap.web.vo;

import java.util.Map;

/**
 * 集成状态响应。
 *
 * @param components  各组件健康状态，如 {@code db=UP}
 * @param mapProvider 当前生效的地图 Provider
 */
public record IntegrationStatusVo(Map<String, String> components, String mapProvider) {
}
