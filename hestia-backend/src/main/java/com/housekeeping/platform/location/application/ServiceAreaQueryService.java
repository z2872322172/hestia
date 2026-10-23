package com.housekeeping.platform.location.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.location.domain.ServiceAreaStatus;
import com.housekeeping.platform.location.persistence.entity.ServiceAreaEntity;
import com.housekeeping.platform.location.persistence.mapper.ServiceAreaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务区域查询服务（跨模块只读契约）。
 *
 * <p>其他模块（账号区域授权、人员区域分配、派单筛选）只需要"这个区域是否可用于新业务"，
 * 不需要也不应该拿到 {@code ServiceAreaMapper}。把判定收在这里，
 * 归档区域不能被重新授权的规则就只有一处实现。</p>
 */
@Service
@RequiresPersistence
public class ServiceAreaQueryService {

    private final ServiceAreaMapper serviceAreaMapper;

    public ServiceAreaQueryService(ServiceAreaMapper serviceAreaMapper) {
        this.serviceAreaMapper = serviceAreaMapper;
    }

    /**
     * 按 ID 取服务区域（不限状态，仅用于展示）。
     *
     * <p>与 {@link #requireActive} 的区别：展示历史关系时，归档片区的名字仍然要显示出来，
     * 否则人员的区域列表里会出现"ID 12"这种没有意义的占位。
     * 但"能不能分配给新业务"必须用 {@code requireActive}。</p>
     *
     * @param serviceAreaId 服务区域 ID
     * @return 服务区域
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceAreaEntity requireById(long serviceAreaId) {
        ServiceAreaEntity entity = serviceAreaMapper.selectById(serviceAreaId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 按 ID 取可用于新业务的服务区域。
     *
     * @param serviceAreaId 服务区域 ID
     * @return 服务区域
     * @throws BusinessException 不存在、非 ACTIVE 或已归档
     */
    @Transactional(readOnly = true)
    public ServiceAreaEntity requireActive(long serviceAreaId) {
        ServiceAreaEntity entity = serviceAreaMapper.selectById(serviceAreaId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_NOT_FOUND);
        }
        if (ServiceAreaStatus.ARCHIVED.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_ARCHIVED);
        }
        if (!ServiceAreaStatus.ACTIVE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_AREA_NOT_ACTIVE);
        }
        return entity;
    }
}
