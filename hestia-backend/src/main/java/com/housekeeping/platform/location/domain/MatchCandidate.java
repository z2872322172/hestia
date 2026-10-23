package com.housekeeping.platform.location.domain;

import java.time.LocalDateTime;

/**
 * 匹配候选（一条"服务区域覆盖了某行政区"的记录）。
 *
 * @param serviceAreaId    服务区域 ID
 * @param areaNo           服务区域编号
 * @param areaName         服务区域名称
 * @param coverageLevel    覆盖级别
 * @param priority         服务区域优先级，越大越优先
 * @param effectiveFrom    生效开始时间，可为 {@code null}（视为最早生效）
 * @param matchedRegionCode 命中的行政区编码，用于回传"按哪个级别命中的"
 */
public record MatchCandidate(long serviceAreaId, String areaNo, String areaName,
                             CoverageLevel coverageLevel, int priority,
                             LocalDateTime effectiveFrom, String matchedRegionCode) {
}
