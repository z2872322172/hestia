package com.housekeeping.platform.workforce.application;

import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.view.ServiceItemRef;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.workforce.application.view.QualificationRequirementView;
import com.housekeeping.platform.workforce.domain.CertificateType;
import com.housekeeping.platform.workforce.persistence.entity.QualificationRequirementEntity;
import com.housekeeping.platform.workforce.persistence.mapper.QualificationRequirementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务项目资质要求维护（运营端）。
 *
 * <p>要求由服务项目决定（例如"空调清洗需要特种设备作业证"），
 * 人员模块读取它来判定资格。整体替换而不是增删单项：
 * 要求集合很小，增量接口要额外处理"改成非必需"与"删掉"的区别，反而更容易出错。</p>
 */
@Service
@RequiresPersistence
public class QualificationRequirementService {

    private final QualificationRequirementMapper requirementMapper;

    private final ServiceCatalogQueryService catalogQueryService;

    public QualificationRequirementService(QualificationRequirementMapper requirementMapper,
                                           ServiceCatalogQueryService catalogQueryService) {
        this.requirementMapper = requirementMapper;
        this.catalogQueryService = catalogQueryService;
    }

    /**
     * 查询服务项目的资质要求。
     *
     * @param serviceItemNo 服务项目编号
     * @return 要求列表
     */
    @Transactional(readOnly = true)
    public List<QualificationRequirementView> list(String serviceItemNo) {
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        return requirementMapper.selectActiveByItemId(item.serviceItemId()).stream()
                .map(entity -> new QualificationRequirementView(entity.getCertificateType(),
                        Boolean.TRUE.equals(entity.getRequired())))
                .toList();
    }

    /**
     * 整体替换服务项目的资质要求。
     *
     * @param serviceItemNo 服务项目编号
     * @param requirements  要求列表
     * @param operatorId    操作人账号 ID
     * @return 替换后的要求列表
     */
    @Transactional
    public List<QualificationRequirementView> replace(String serviceItemNo,
                                                      List<QualificationRequirementView> requirements,
                                                      long operatorId) {
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        requirementMapper.deleteByItemId(item.serviceItemId());
        if (requirements == null) {
            return List.of();
        }
        Set<CertificateType> seen = new HashSet<>();
        for (QualificationRequirementView requirement : requirements) {
            CertificateType type;
            try {
                type = CertificateType.of(requirement.certificateType());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.WORKER_CERTIFICATE_INVALID, exception.getMessage());
            }
            if (!seen.add(type)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "证书类型重复：" + type.name());
            }
            QualificationRequirementEntity entity = new QualificationRequirementEntity();
            entity.setServiceItemId(item.serviceItemId());
            entity.setCertificateType(type.name());
            entity.setRequired(requirement.required());
            entity.setStatus("ACTIVE");
            entity.setUpdatedByAccountId(operatorId);
            requirementMapper.insert(entity);
        }
        return list(serviceItemNo);
    }
}
