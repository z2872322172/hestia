package com.housekeeping.platform.workforce.api.dto;

import java.util.List;

/**
 * 整体替换服务区域请求。
 *
 * @param serviceAreaIds 服务区域 ID 列表，空列表表示清空
 */
public record ReplaceServiceAreasRequest(List<Long> serviceAreaIds) {
}
