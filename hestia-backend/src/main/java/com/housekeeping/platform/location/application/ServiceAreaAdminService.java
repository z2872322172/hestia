package com.housekeeping.platform.location.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.location.application.command.CreateServiceAreaCommand;
import com.housekeeping.platform.location.application.command.UpdateServiceAreaCommand;
import com.housekeeping.platform.location.application.view.CoverageView;
import com.housekeeping.platform.location.application.view.ServiceAreaPublicView;
import com.housekeeping.platform.location.application.view.ServiceAreaView;
import com.housekeeping.platform.location.domain.CoverageLevel;
import com.housekeeping.platform.location.domain.RegionLevel;
import com.housekeeping.platform.location.domain.ServiceAreaStatus;
import com.housekeeping.platform.location.persistence.entity.AdministrativeRegionEntity;
import com.housekeeping.platform.location.persistence.entity.ServiceAreaCoverageEntity;
import com.housekeeping.platform.location.persistence.entity.ServiceAreaEntity;
import com.housekeeping.platform.location.persistence.mapper.AdministrativeRegionMapper;
import com.housekeeping.platform.location.persistence.mapper.ServiceAreaCoverageMapper;
import com.housekeeping.platform.location.persistence.mapper.ServiceAreaMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 服务区域维护（运营后台）。
 *
 * <p>三条必须守住的规则：</p>
 * <ol>
 *   <li>启用前必须至少有一条有效覆盖——否则"启用了却匹配不到任何地址"，
 *       运营会以为已上线；</li>
 *   <li>覆盖的行政区必须属于服务区域所在城市，且覆盖级别与行政区级别一致；</li>
 *   <li>任何影响匹配结果的变更都要通知客户模块把相关地址标记为 {@code STALE}，
 *       否则地址簿会一直拿着过期的 {@code MATCHED} 去下单。</li>
 * </ol>
 */
@Service
@RequiresPersistence
public class ServiceAreaAdminService {

    private static final String AREA_NO_SEQ_KEY = "SERVICE_AREA";

    private static final String AREA_NO_PREFIX = "SA";

    private static final String DEFAULT_AREA_TYPE = "DISTRICT_GROUP";

    private static final int MAX_PRIORITY = 999;

    private final ServiceAreaMapper serviceAreaMapper;

    private final ServiceAreaCoverageMapper coverageMapper;

    private final AdministrativeRegionMapper regionMapper;

    private final RegionDictionaryService regionDictionaryService;

    private final BusinessNoGenerator businessNoGenerator;

    private final ObjectProvider<ServiceAreaChangeHandler> changeHandlers;

    public ServiceAreaAdminService(ServiceAreaMapper serviceAreaMapper,
                                   ServiceAreaCoverageMapper coverageMapper,
                                   AdministrativeRegionMapper regionMapper,
                                   RegionDictionaryService regionDictionaryService,
                                   BusinessNoGenerator businessNoGenerator,
                                   ObjectProvider<ServiceAreaChangeHandler> changeHandlers) {
        this.serviceAreaMapper = serviceAreaMapper;
        this.coverageMapper = coverageMapper;
        this.regionMapper = regionMapper;
        this.regionDictionaryService = regionDictionaryService;
        this.businessNoGenerator = businessNoGenerator;
        this.changeHandlers = changeHandlers;
    }

    /**
     * 创建服务区域（初始状态 DRAFT，不参与匹配）。
     *
     * @param command 创建命令
     * @return 新建的服务区域
     */
    @Transactional
    public ServiceAreaView create(CreateServiceAreaCommand command) {
        AdministrativeRegionEntity city = regionDictionaryService.requireActiveCity(command.cityCode());
        validateEffectiveRange(command.effectiveFrom(), command.effectiveTo());

        ServiceAreaEntity entity = new ServiceAreaEntity();
        entity.setAreaNo(businessNoGenerator.next(AREA_NO_SEQ_KEY, AREA_NO_PREFIX));
        entity.setAreaName(command.areaName());
        entity.setDescription(command.description());
        entity.setCityRegionId(city.getId());
        entity.setAreaType(command.areaType() == null || command.areaType().isBlank()
                ? DEFAULT_AREA_TYPE : command.areaType());
        entity.setPriority(validatePriority(command.priority()));
        entity.setStatus(ServiceAreaStatus.DRAFT.name());
        entity.setEffectiveFrom(command.effectiveFrom());
        entity.setEffectiveTo(command.effectiveTo());
        serviceAreaMapper.insert(entity);
        return toView(entity);
    }

    /**
     * 修改服务区域基础信息。
     *
     * @param areaNo  服务区域编号
     * @param command 修改命令（含版本号）
     * @return 修改后的服务区域
     */
    @Transactional
    public ServiceAreaView update(String areaNo, UpdateServiceAreaCommand command) {
        ServiceAreaEntity entity = requireArea(areaNo);
        if (entity.getStatus().equals(ServiceAreaStatus.ARCHIVED.name())) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_ARCHIVED);
        }
        validateEffectiveRange(command.effectiveFrom(), command.effectiveTo());

        entity.setAreaName(command.areaName());
        entity.setDescription(command.description());
        entity.setPriority(validatePriority(command.priority()));
        entity.setEffectiveFrom(command.effectiveFrom());
        entity.setEffectiveTo(command.effectiveTo());
        // 乐观锁：把客户端版本写回实体，MyBatis-Plus 会拼 WHERE version = ?
        entity.setVersion(command.version());
        if (serviceAreaMapper.updateById(entity) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_VERSION_CONFLICT);
        }
        notifyChanged(entity.getId(), "服务区域基础信息变更");
        return toView(requireArea(areaNo));
    }

    /**
     * 状态流转。
     *
     * @param areaNo 服务区域编号
     * @param target 目标状态
     * @return 变更后的服务区域
     */
    @Transactional
    public ServiceAreaView changeStatus(String areaNo, ServiceAreaStatus target) {
        ServiceAreaEntity entity = requireArea(areaNo);
        ServiceAreaStatus current = ServiceAreaStatus.valueOf(entity.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_NOT_ACTIVE,
                    "服务区域不能从 " + current + " 变更为 " + target);
        }
        if (target == ServiceAreaStatus.ACTIVE && coverageMapper.countActiveByServiceAreaId(entity.getId()) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_COVERAGE_REQUIRED);
        }
        entity.setStatus(target.name());
        entity.setVersion(null);
        if (serviceAreaMapper.updateById(entity) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_VERSION_CONFLICT);
        }
        notifyChanged(entity.getId(), "服务区域状态变更为 " + target);
        return toView(requireArea(areaNo));
    }

    /**
     * 查询城市下的全部服务区域（含草稿与停用）。
     *
     * @param cityCode 城市行政区编码
     * @return 服务区域列表
     */
    @Transactional(readOnly = true)
    public List<ServiceAreaView> list(String cityCode) {
        AdministrativeRegionEntity city = regionDictionaryService.requireActiveCity(cityCode);
        return serviceAreaMapper.selectAllByCity(city.getId()).stream().map(this::toView).toList();
    }

    /**
     * 查询服务区域详情。
     *
     * @param areaNo 编号
     * @return 服务区域
     */
    @Transactional(readOnly = true)
    public ServiceAreaView detail(String areaNo) {
        return toView(requireArea(areaNo));
    }

    /**
     * 查询城市下对消费者公开的服务区域。
     *
     * @param cityCode 城市行政区编码
     * @return 公开视图列表
     */
    @Transactional(readOnly = true)
    public List<ServiceAreaPublicView> listPublic(String cityCode) {
        AdministrativeRegionEntity city = regionDictionaryService.requireActiveCity(cityCode);
        return serviceAreaMapper.selectActiveByCity(city.getId()).stream()
                .map(area -> new ServiceAreaPublicView(area.getAreaNo(), area.getAreaName(), area.getDescription()))
                .toList();
    }

    /**
     * 查询覆盖范围（含已停用）。
     *
     * @param areaNo 服务区域编号
     * @return 覆盖列表
     */
    @Transactional(readOnly = true)
    public List<CoverageView> listCoverages(String areaNo) {
        ServiceAreaEntity area = requireArea(areaNo);
        return coverageMapper.selectByServiceAreaId(area.getId()).stream()
                .map(coverage -> {
                    AdministrativeRegionEntity region = regionMapper.selectById(coverage.getRegionId());
                    return new CoverageView(coverage.getId(),
                            region == null ? null : region.getRegionCode(),
                            region == null ? null : region.getRegionName(),
                            coverage.getCoverageLevel(), coverage.getStatus());
                })
                .toList();
    }

    /**
     * 新增覆盖范围。
     *
     * @param areaNo     服务区域编号
     * @param regionCode 行政区编码（区县或街道）
     * @return 新建的覆盖记录
     */
    @Transactional
    public CoverageView addCoverage(String areaNo, String regionCode) {
        ServiceAreaEntity area = requireArea(areaNo);
        if (ServiceAreaStatus.ARCHIVED.name().equals(area.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_ARCHIVED);
        }
        AdministrativeRegionEntity region = regionDictionaryService.requireByCode(regionCode);

        CoverageLevel level = switch (region.getRegionLevel()) {
            case "DISTRICT" -> CoverageLevel.DISTRICT;
            case "STREET" -> CoverageLevel.STREET;
            default -> throw new BusinessException(ErrorCode.SERVICE_AREA_COVERAGE_LEVEL_MISMATCH,
                    "覆盖范围只能是区县或街道");
        };
        assertRegionBelongsToCity(area, region, level);

        ServiceAreaCoverageEntity existing = coverageMapper.selectByAreaAndRegion(area.getId(), region.getId());
        if (existing != null) {
            if ("ACTIVE".equals(existing.getStatus())) {
                throw new BusinessException(ErrorCode.SERVICE_AREA_COVERAGE_CONFLICT);
            }
            // 曾经停用过的覆盖重新启用，不新增行，保留配置历史
            existing.setStatus("ACTIVE");
            coverageMapper.updateById(existing);
            notifyChanged(area.getId(), "覆盖范围重新启用：" + regionCode);
            return new CoverageView(existing.getId(), region.getRegionCode(), region.getRegionName(),
                    existing.getCoverageLevel(), existing.getStatus());
        }

        ServiceAreaCoverageEntity entity = new ServiceAreaCoverageEntity();
        entity.setServiceAreaId(area.getId());
        entity.setRegionId(region.getId());
        entity.setCoverageLevel(level.name());
        entity.setStatus("ACTIVE");
        coverageMapper.insert(entity);
        notifyChanged(area.getId(), "新增覆盖范围：" + regionCode);
        return new CoverageView(entity.getId(), region.getRegionCode(), region.getRegionName(),
                entity.getCoverageLevel(), entity.getStatus());
    }

    /**
     * 停用覆盖范围（不删除行）。
     *
     * @param areaNo     服务区域编号
     * @param coverageId 覆盖记录 ID
     */
    @Transactional
    public void disableCoverage(String areaNo, long coverageId) {
        ServiceAreaEntity area = requireArea(areaNo);
        ServiceAreaCoverageEntity coverage = coverageMapper.selectById(coverageId);
        if (coverage == null || !Objects.equals(coverage.getServiceAreaId(), area.getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "覆盖记录不存在");
        }
        if (!"ACTIVE".equals(coverage.getStatus())) {
            return;
        }
        coverage.setStatus("DISABLED");
        coverageMapper.updateById(coverage);
        notifyChanged(area.getId(), "停用覆盖范围 id=" + coverageId);
    }

    private void assertRegionBelongsToCity(ServiceAreaEntity area, AdministrativeRegionEntity region,
                                           CoverageLevel level) {
        Long cityRegionId = area.getCityRegionId();
        if (level == CoverageLevel.DISTRICT) {
            // 区县必须直接挂在该城市下
            if (!Objects.equals(region.getParentId(), cityRegionId)) {
                throw new BusinessException(ErrorCode.SERVICE_AREA_COVERAGE_OUTSIDE_CITY);
            }
            return;
        }
        // 街道：其上级区县必须属于该城市
        AdministrativeRegionEntity district = region.getParentId() == null
                ? null : regionMapper.selectById(region.getParentId());
        if (district == null
                || !RegionLevel.DISTRICT.name().equals(district.getRegionLevel())
                || !Objects.equals(district.getParentId(), cityRegionId)) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_COVERAGE_OUTSIDE_CITY);
        }
    }

    private void notifyChanged(long serviceAreaId, String reason) {
        changeHandlers.orderedStream().forEach(handler -> handler.onServiceAreaChanged(serviceAreaId, reason));
    }

    private ServiceAreaEntity requireArea(String areaNo) {
        ServiceAreaEntity entity = serviceAreaMapper.selectByAreaNo(areaNo);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_NOT_FOUND);
        }
        return entity;
    }

    private int validatePriority(Integer priority) {
        int value = priority == null ? 0 : priority;
        if (value < 0 || value > MAX_PRIORITY) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "优先级需在 0 至 " + MAX_PRIORITY + " 之间");
        }
        return value;
    }

    private void validateEffectiveRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_EFFECTIVE_RANGE_INVALID);
        }
    }

    private ServiceAreaView toView(ServiceAreaEntity entity) {
        AdministrativeRegionEntity city = regionMapper.selectById(entity.getCityRegionId());
        return new ServiceAreaView(entity.getAreaNo(), entity.getAreaName(), entity.getDescription(),
                city == null ? null : city.getRegionCode(),
                city == null ? null : city.getRegionName(),
                entity.getAreaType(), entity.getPriority(), entity.getStatus(),
                entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getVersion());
    }
}
