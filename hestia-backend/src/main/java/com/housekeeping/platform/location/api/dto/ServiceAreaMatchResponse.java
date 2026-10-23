package com.housekeeping.platform.location.api.dto;

import com.housekeeping.platform.location.domain.ServiceAreaMatchResult;

import java.time.LocalDateTime;

/**
 * 服务区域匹配响应。
 *
 * <p>只暴露"是否可服务"与区域名称，不暴露候选列表与内部优先级。</p>
 *
 * @param matchStatus       匹配状态
 * @param serviceAreaId     服务区域 ID（仅 MATCHED）
 * @param areaNo            服务区域编号（仅 MATCHED）
 * @param areaName          服务区域名称（仅 MATCHED）
 * @param matchLevel        命中级别（仅 MATCHED）
 * @param matchedRegionCode 命中的行政区编码（仅 MATCHED）
 * @param matchedAt         匹配时间
 * @param message           提示信息（失败时）
 */
public record ServiceAreaMatchResponse(String matchStatus, Long serviceAreaId, String areaNo,
                                       String areaName, String matchLevel, String matchedRegionCode,
                                       LocalDateTime matchedAt, String message) {

    /**
     * 由领域结果转换。
     *
     * @param result 匹配结果
     * @return 响应体
     */
    public static ServiceAreaMatchResponse of(ServiceAreaMatchResult result) {
        return new ServiceAreaMatchResponse(
                result.matchStatus().name(),
                result.serviceAreaId(),
                result.areaNo(),
                result.areaName(),
                result.matchLevel() == null ? null : result.matchLevel().name(),
                result.matchedRegionCode(),
                result.matchedAt(),
                result.message());
    }
}
