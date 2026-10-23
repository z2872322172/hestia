package com.housekeeping.platform.location.application;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.location.domain.AreaMatchStatus;
import com.housekeeping.platform.location.domain.CoverageLevel;
import com.housekeeping.platform.location.domain.MatchCandidate;
import com.housekeeping.platform.location.domain.MatchRanking;
import com.housekeeping.platform.location.domain.RegionLevel;
import com.housekeeping.platform.location.domain.ServiceAreaMatchQuery;
import com.housekeeping.platform.location.domain.ServiceAreaMatchResult;
import com.housekeeping.platform.location.persistence.entity.AdministrativeRegionEntity;
import com.housekeeping.platform.location.persistence.mapper.AdministrativeRegionMapper;
import com.housekeeping.platform.location.persistence.mapper.ServiceAreaCoverageMapper;
import com.housekeeping.platform.location.persistence.row.MatchCandidateRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 地址到服务区域的匹配服务（跨模块公开契约 {@code ServiceAreaMatchService}）。
 *
 * <p>匹配步骤（docs/modules/03 第 7.2 节）：</p>
 * <pre>
 * 校验行政区层级与状态 -> 查询街道/区县覆盖 -> 过滤非 ACTIVE 与未生效
 * -> 按"覆盖级别 > priority > effective_from"排序 -> 返回唯一服务区域
 * </pre>
 *
 * <p>本服务是<b>全函数</b>：行政区编码非法也返回 {@link AreaMatchStatus#REGION_INVALID}，
 * 而不是抛异常。因为同一个输入在"公共匹配接口"里应表现为 200 + 明确状态，
 * 在"保存地址"里应表现为 400 参数错误——由调用方决定怎么呈现，匹配服务不替它决定。</p>
 */
@Service
@RequiresPersistence
public class ServiceAreaMatchService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AdministrativeRegionMapper regionMapper;

    private final ServiceAreaCoverageMapper coverageMapper;

    public ServiceAreaMatchService(AdministrativeRegionMapper regionMapper,
                                   ServiceAreaCoverageMapper coverageMapper) {
        this.regionMapper = regionMapper;
        this.coverageMapper = coverageMapper;
    }

    /**
     * 匹配服务区域。
     *
     * @param query 匹配输入，城市与区县编码必填
     * @return 匹配结果，永不返回 {@code null}
     */
    @Transactional(readOnly = true)
    public ServiceAreaMatchResult match(ServiceAreaMatchQuery query) {
        AdministrativeRegionEntity city = regionMapper.selectByCode(query.cityCode());
        String cityInvalid = validateRegion(city, RegionLevel.CITY, null, "城市");
        if (cityInvalid != null) {
            return ServiceAreaMatchResult.failed(AreaMatchStatus.REGION_INVALID, cityInvalid);
        }

        AdministrativeRegionEntity district = regionMapper.selectByCode(query.districtCode());
        String districtInvalid = validateRegion(district, RegionLevel.DISTRICT, city.getId(), "区县");
        if (districtInvalid != null) {
            return ServiceAreaMatchResult.failed(AreaMatchStatus.REGION_INVALID, districtInvalid);
        }

        List<Long> regionIds = new ArrayList<>();
        regionIds.add(district.getId());

        if (query.streetCode() != null && !query.streetCode().isBlank()) {
            AdministrativeRegionEntity street = regionMapper.selectByCode(query.streetCode());
            String streetInvalid = validateRegion(street, RegionLevel.STREET, district.getId(), "街道");
            if (streetInvalid != null) {
                return ServiceAreaMatchResult.failed(AreaMatchStatus.REGION_INVALID, streetInvalid);
            }
            regionIds.add(street.getId());
        }

        List<MatchCandidate> candidates = coverageMapper.selectMatchCandidates(city.getId(), regionIds)
                .stream()
                .map(this::toCandidate)
                .toList();
        if (candidates.isEmpty()) {
            return ServiceAreaMatchResult.failed(AreaMatchStatus.OUT_OF_RANGE, "当前地址暂不在服务范围内");
        }

        List<MatchCandidate> ranked = MatchRanking.rank(candidates);
        if (MatchRanking.isAmbiguous(ranked)) {
            return ServiceAreaMatchResult.failed(AreaMatchStatus.AMBIGUOUS,
                    "当前地址匹配到多个同优先级服务区域");
        }
        return ServiceAreaMatchResult.matched(ranked.get(0), LocalDateTime.now());
    }

    /**
     * 校验行政区是否存在、层级是否正确、上级是否匹配、状态是否可用。
     *
     * @param region          行政区
     * @param expectedLevel   期望层级
     * @param expectedParentId 期望的上级 ID，为 {@code null} 表示不校验上级
     * @param label           用于拼提示语的层级名称
     * @return 校验失败返回提示语；通过返回 {@code null}
     */
    private String validateRegion(AdministrativeRegionEntity region, RegionLevel expectedLevel,
                                  Long expectedParentId, String label) {
        if (region == null) {
            return label + "行政区编码不存在";
        }
        if (!expectedLevel.name().equals(region.getRegionLevel())) {
            return label + "行政区层级不正确";
        }
        if (expectedParentId != null && !Objects.equals(region.getParentId(), expectedParentId)) {
            return label + "行政区不属于所选上级行政区";
        }
        if (!STATUS_ACTIVE.equals(region.getStatus())) {
            return label + "行政区已停用";
        }
        return null;
    }

    private MatchCandidate toCandidate(MatchCandidateRow row) {
        return new MatchCandidate(
                row.getServiceAreaId(),
                row.getAreaNo(),
                row.getAreaName(),
                CoverageLevel.valueOf(row.getCoverageLevel()),
                row.getPriority() == null ? 0 : row.getPriority(),
                row.getEffectiveFrom(),
                row.getMatchedRegionCode());
    }
}
