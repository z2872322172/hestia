package com.housekeeping.platform.location.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.location.application.command.RegionImportItem;
import com.housekeeping.platform.location.application.view.RegionView;
import com.housekeeping.platform.location.domain.RegionLevel;
import com.housekeeping.platform.location.persistence.entity.AdministrativeRegionEntity;
import com.housekeeping.platform.location.persistence.mapper.AdministrativeRegionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 行政区字典服务。
 *
 * <p>行政区字典由管理员统一导入，运营人员不得直接修改国家行政区编码：
 * 编码一旦被改，历史地址与服务区域覆盖会全部指错。</p>
 */
@Service
@RequiresPersistence
public class RegionDictionaryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 导入时必须按层级从高到低处理，否则子级找不到刚插入的父级。 */
    private static final Comparator<RegionImportItem> BY_LEVEL = Comparator.comparingInt(
            item -> levelOf(item.regionLevel()).ordinal());

    private final AdministrativeRegionMapper regionMapper;

    public RegionDictionaryService(AdministrativeRegionMapper regionMapper) {
        this.regionMapper = regionMapper;
    }

    /**
     * 幂等导入行政区。
     *
     * @param items 导入项
     * @return 新增或更新的条数
     */
    @Transactional
    public int importRegions(List<RegionImportItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "导入内容不能为空");
        }
        List<RegionImportItem> ordered = items.stream().sorted(BY_LEVEL).toList();
        int affected = 0;
        for (RegionImportItem item : ordered) {
            affected += upsert(item);
        }
        return affected;
    }

    /**
     * 查询某行政区的下级。
     *
     * @param parentCode 上级行政区编码；为空时返回所有省级
     * @return 下级列表
     */
    @Transactional(readOnly = true)
    public List<RegionView> listChildren(String parentCode) {
        List<AdministrativeRegionEntity> regions;
        if (parentCode == null || parentCode.isBlank()) {
            regions = regionMapper.selectActiveByLevel(RegionLevel.PROVINCE.name());
        } else {
            AdministrativeRegionEntity parent = requireByCode(parentCode);
            regions = regionMapper.selectActiveChildren(parent.getId());
        }
        return regions.stream().map(this::toView).toList();
    }

    /**
     * 按编码取行政区，不存在即报错。
     *
     * @param regionCode 行政区编码
     * @return 行政区
     */
    @Transactional(readOnly = true)
    public AdministrativeRegionEntity requireByCode(String regionCode) {
        AdministrativeRegionEntity region = regionMapper.selectByCode(regionCode);
        if (region == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        return region;
    }

    /**
     * 按主键取行政区，不存在即报错。
     *
     * @param regionId 行政区 ID
     * @return 行政区
     */
    @Transactional(readOnly = true)
    public AdministrativeRegionEntity requireById(Long regionId) {
        AdministrativeRegionEntity region = regionId == null ? null : regionMapper.selectById(regionId);
        if (region == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        return region;
    }

    /**
     * 取城市级行政区，并校验层级与状态。
     *
     * @param cityCode 城市行政区编码
     * @return 城市行政区
     */
    @Transactional(readOnly = true)
    public AdministrativeRegionEntity requireActiveCity(String cityCode) {
        AdministrativeRegionEntity city = requireByCode(cityCode);
        if (!RegionLevel.CITY.name().equals(city.getRegionLevel())) {
            throw new BusinessException(ErrorCode.REGION_LEVEL_MISMATCH, "请选择城市级行政区");
        }
        if (!STATUS_ACTIVE.equals(city.getStatus())) {
            throw new BusinessException(ErrorCode.REGION_DISABLED);
        }
        return city;
    }

    /**
     * 由区县编码反推所属城市编码。
     *
     * <p>客户地址只强制填写区县（docs/modules/02 第 11.2 节 {@code district_code} 必填、
     * {@code city_code} 可空），但服务区域匹配需要城市。与其让前端自己拼，
     * 不如由服务端按行政区字典推导——字典是唯一事实来源，前端传的城市编码可能是错的。</p>
     *
     * @param districtCode 区县行政区编码
     * @return 城市行政区编码
     */
    @Transactional(readOnly = true)
    public String cityCodeOfDistrict(String districtCode) {
        AdministrativeRegionEntity district = requireByCode(districtCode);
        if (!RegionLevel.DISTRICT.name().equals(district.getRegionLevel()) || district.getParentId() == null) {
            throw new BusinessException(ErrorCode.REGION_LEVEL_MISMATCH, "区县行政区编码不正确");
        }
        AdministrativeRegionEntity city = regionMapper.selectById(district.getParentId());
        if (city == null || !RegionLevel.CITY.name().equals(city.getRegionLevel())) {
            throw new BusinessException(ErrorCode.REGION_PARENT_INVALID);
        }
        return city.getRegionCode();
    }

    private int upsert(RegionImportItem item) {
        RegionLevel level = levelOf(item.regionLevel());
        Long parentId = null;
        if (item.parentCode() != null && !item.parentCode().isBlank()) {
            AdministrativeRegionEntity parent = regionMapper.selectByCode(item.parentCode());
            if (parent == null) {
                throw new BusinessException(ErrorCode.REGION_PARENT_INVALID,
                        "上级行政区不存在：" + item.parentCode());
            }
            RegionLevel expectedParentLevel = expectedParentLevel(level);
            if (!expectedParentLevel.name().equals(parent.getRegionLevel())) {
                throw new BusinessException(ErrorCode.REGION_PARENT_INVALID,
                        "行政区 " + item.regionCode() + " 的上级层级应为 " + expectedParentLevel);
            }
            parentId = parent.getId();
        } else if (level != RegionLevel.PROVINCE) {
            throw new BusinessException(ErrorCode.REGION_PARENT_INVALID,
                    "非省级行政区必须指定上级：" + item.regionCode());
        }

        AdministrativeRegionEntity existing = regionMapper.selectByCode(item.regionCode());
        if (existing == null) {
            AdministrativeRegionEntity entity = new AdministrativeRegionEntity();
            entity.setRegionCode(item.regionCode());
            entity.setParentId(parentId);
            entity.setRegionLevel(level.name());
            entity.setRegionName(item.regionName());
            entity.setShortName(item.shortName());
            entity.setStatus(STATUS_ACTIVE);
            entity.setSortOrder(item.sortOrder() == null ? 0 : item.sortOrder());
            regionMapper.insert(entity);
            return 1;
        }
        existing.setParentId(parentId);
        existing.setRegionLevel(level.name());
        existing.setRegionName(item.regionName());
        existing.setShortName(item.shortName());
        if (item.sortOrder() != null) {
            existing.setSortOrder(item.sortOrder());
        }
        regionMapper.updateById(existing);
        return 1;
    }

    private static RegionLevel levelOf(String regionLevel) {
        return RegionLevel.of(regionLevel)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_LEVEL_MISMATCH,
                        "行政区层级非法：" + regionLevel));
    }

    private static RegionLevel expectedParentLevel(RegionLevel level) {
        return switch (level) {
            case CITY -> RegionLevel.PROVINCE;
            case DISTRICT -> RegionLevel.CITY;
            case STREET -> RegionLevel.DISTRICT;
            case PROVINCE -> throw new BusinessException(ErrorCode.REGION_PARENT_INVALID,
                    "省级行政区不能指定上级");
        };
    }

    private RegionView toView(AdministrativeRegionEntity entity) {
        String parentCode = null;
        if (entity.getParentId() != null) {
            AdministrativeRegionEntity parent = regionMapper.selectById(entity.getParentId());
            parentCode = parent == null ? null : parent.getRegionCode();
        }
        return new RegionView(entity.getId(), entity.getRegionCode(), parentCode,
                entity.getRegionLevel(), entity.getRegionName(), entity.getStatus());
    }
}
