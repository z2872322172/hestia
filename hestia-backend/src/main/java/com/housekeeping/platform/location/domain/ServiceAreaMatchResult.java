package com.housekeeping.platform.location.domain;

import java.time.LocalDateTime;

/**
 * 地址到服务区域的匹配结果。
 *
 * <p>公共接口只返回本结构，<b>不</b>返回候选列表与 {@code priority}：
 * 内部优先级属于经营配置，暴露出去等于把定价与派单策略公开。</p>
 *
 * @param matchStatus       匹配状态
 * @param serviceAreaId     服务区域 ID，仅 MATCHED 时非空
 * @param areaNo            服务区域编号，仅 MATCHED 时非空
 * @param areaName          服务区域名称，仅 MATCHED 时非空
 * @param matchLevel        命中级别（DISTRICT / STREET），仅 MATCHED 时非空
 * @param matchedRegionCode 命中的行政区编码，仅 MATCHED 时非空
 * @param matchedAt         匹配时间
 * @param message           面向用户的提示，失败时非空
 */
public record ServiceAreaMatchResult(AreaMatchStatus matchStatus, Long serviceAreaId, String areaNo,
                                     String areaName, CoverageLevel matchLevel, String matchedRegionCode,
                                     LocalDateTime matchedAt, String message) {

    /**
     * 构造匹配成功结果。
     *
     * @param candidate 命中的候选
     * @param matchedAt 匹配时间
     * @return 匹配结果
     */
    public static ServiceAreaMatchResult matched(MatchCandidate candidate, LocalDateTime matchedAt) {
        return new ServiceAreaMatchResult(AreaMatchStatus.MATCHED, candidate.serviceAreaId(),
                candidate.areaNo(), candidate.areaName(), candidate.coverageLevel(),
                candidate.matchedRegionCode(), matchedAt, null);
    }

    /**
     * 构造失败结果。
     *
     * @param status  失败状态
     * @param message 面向用户的提示
     * @return 匹配结果
     */
    public static ServiceAreaMatchResult failed(AreaMatchStatus status, String message) {
        return new ServiceAreaMatchResult(status, null, null, null, null, null, LocalDateTime.now(), message);
    }

    /**
     * 是否匹配成功。
     *
     * @return 匹配成功返回 {@code true}
     */
    public boolean isMatched() {
        return matchStatus == AreaMatchStatus.MATCHED;
    }
}
