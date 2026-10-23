package com.housekeeping.platform.workforce.application;

import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.view.ServiceItemRef;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.application.contract.AccountRef;
import com.housekeeping.platform.identity.application.contract.AccountRefQueryService;
import com.housekeeping.platform.workforce.application.view.EligibilityView;
import com.housekeeping.platform.workforce.domain.AuditStatus;
import com.housekeeping.platform.workforce.domain.CertificateType;
import com.housekeeping.platform.workforce.domain.CertificateUsability;
import com.housekeeping.platform.workforce.domain.EligibilityResult;
import com.housekeeping.platform.workforce.domain.EligibilityEvaluator;
import com.housekeeping.platform.workforce.domain.WorkStatus;
import com.housekeeping.platform.workforce.domain.WorkerFacts;
import com.housekeeping.platform.workforce.persistence.entity.QualificationRequirementEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerCertificateEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerProfileEntity;
import com.housekeeping.platform.workforce.persistence.mapper.QualificationRequirementMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerCertificateMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerServiceAreaMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerSkillMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可派单资格查询（跨模块契约，docs/modules/05 第 9、17.3 节）。
 *
 * <p>本服务只负责<b>取事实</b>，判定逻辑全在 {@link EligibilityEvaluator} 里。
 * 这样"健康证过期就不能派单"这条规则可以脱离数据库被测到，
 * 而不是只能靠跑一遍真实环境去猜。</p>
 *
 * <p>区域覆盖目前按"订单区域是否在人员已启用的区域列表中"精确匹配。
 * 层级覆盖（覆盖天河区 ⇒ 覆盖五山街道）需要用到片区与行政区的映射，
 * 那是派单模块筛选候选人的职责（批次 4），两处各写一遍必然出现两种口径。</p>
 */
@Service
@RequiresPersistence
public class WorkerEligibilityService {

    private final WorkerProfileService profileService;

    private final WorkerSkillMapper skillMapper;

    private final WorkerCertificateMapper certificateMapper;

    private final WorkerServiceAreaMapper serviceAreaMapper;

    private final QualificationRequirementMapper requirementMapper;

    private final ServiceCatalogQueryService catalogQueryService;

    private final AccountRefQueryService accountRefQueryService;

    public WorkerEligibilityService(WorkerProfileService profileService,
                                    WorkerSkillMapper skillMapper,
                                    WorkerCertificateMapper certificateMapper,
                                    WorkerServiceAreaMapper serviceAreaMapper,
                                    QualificationRequirementMapper requirementMapper,
                                    ServiceCatalogQueryService catalogQueryService,
                                    AccountRefQueryService accountRefQueryService) {
        this.profileService = profileService;
        this.skillMapper = skillMapper;
        this.certificateMapper = certificateMapper;
        this.serviceAreaMapper = serviceAreaMapper;
        this.requirementMapper = requirementMapper;
        this.catalogQueryService = catalogQueryService;
        this.accountRefQueryService = accountRefQueryService;
    }

    /**
     * 判定某人员能否接某个服务项目（可指定服务区域）。
     *
     * @param workerNo      人员编号
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 订单所在服务区域，{@code null} 表示本次不校验区域
     * @return 资格视图（含全部不通过原因）
     */
    @Transactional(readOnly = true)
    public EligibilityView assess(String workerNo, String serviceItemNo, Long serviceAreaId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        EligibilityResult result = evaluateWorker(worker, item, serviceAreaId);
        List<EligibilityView.Reason> reasons = result.reasons().stream()
                .map(reason -> new EligibilityView.Reason(reason.name(), reason.description()))
                .toList();
        return new EligibilityView(workerNo, item.serviceItemNo(), serviceAreaId, result.eligible(), reasons);
    }

    /**
     * 判断候选人员中是否存在<b>任意一名</b>可接单人员（scheduling 可用性聚合使用）。
     *
     * <p>可用性查询只需要"是否有合格人员"，不需要每个人的不通过原因；
     * 服务项目只加载一次，逐个候选人取事实判定，命中即短路返回。
     * 候选集合来自同一时段的空闲人员，规模是个位数，逐个判定不会放大查询。</p>
     *
     * @param workerIds     候选人员 ID
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 订单所在服务区域，{@code null} 表示不校验区域
     * @return 存在合格人员返回 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean anyEligible(java.util.Collection<Long> workerIds,
                               String serviceItemNo, Long serviceAreaId) {
        if (workerIds == null || workerIds.isEmpty()) {
            return false;
        }
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        for (Long workerId : workerIds) {
            WorkerProfileEntity worker = profileService.requireById(workerId);
            if (evaluateWorker(worker, item, serviceAreaId).eligible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤出全部可接单人员（booking 下单选人使用，docs/modules/07 第 7 节）。
     *
     * <p>与 {@link #anyEligible} 同一条判定链，区别是返回合格人员集合而不是短路布尔值。
     * 入参顺序（时段库存返回顺序）即出参顺序，作为派单候选的默认名次依据。</p>
     *
     * @param workerIds     候选人员 ID（来自时段库存）
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 订单所在服务区域，{@code null} 表示不校验区域
     * @return 合格人员 ID 列表，保持入参相对顺序
     */
    @Transactional(readOnly = true)
    public List<Long> filterEligible(java.util.Collection<Long> workerIds,
                                     String serviceItemNo, Long serviceAreaId) {
        if (workerIds == null || workerIds.isEmpty()) {
            return List.of();
        }
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        List<Long> eligible = new ArrayList<>(workerIds.size());
        for (Long workerId : workerIds) {
            WorkerProfileEntity worker = profileService.requireById(workerId);
            if (evaluateWorker(worker, item, serviceAreaId).eligible()) {
                eligible.add(workerId);
            }
        }
        return List.copyOf(eligible);
    }

    /**
     * 对单个人员执行资格判定。
     *
     * @param worker        人员档案
     * @param item          服务项目摘要
     * @param serviceAreaId 订单区域，可空
     * @return 判定结果
     */
    private EligibilityResult evaluateWorker(WorkerProfileEntity worker, ServiceItemRef item,
                                             Long serviceAreaId) {
        return EligibilityEvaluator.evaluate(collectFacts(worker, item.serviceItemId(), serviceAreaId));
    }

    /**
     * 采集判定事实。
     *
     * @param worker        人员档案
     * @param serviceItemId 服务项目 ID
     * @param serviceAreaId 订单区域，可空
     * @return 判定事实
     */
    private WorkerFacts collectFacts(WorkerProfileEntity worker, long serviceItemId, Long serviceAreaId) {
        AccountRef account = accountRefQueryService.requireRefById(worker.getAccountId());
        Set<Long> skills = new HashSet<>(skillMapper.selectUsableServiceItemIds(worker.getId()));
        Set<CertificateType> required = new LinkedHashSet<>();
        for (QualificationRequirementEntity requirement
                : requirementMapper.selectActiveByItemId(serviceItemId)) {
            if (Boolean.TRUE.equals(requirement.getRequired())) {
                required.add(CertificateType.of(requirement.getCertificateType()));
            }
        }
        Map<CertificateType, CertificateUsability> usability = collectCertificateUsability(worker.getId());
        Set<Long> areas = new HashSet<>(serviceAreaMapper.selectActiveAreaIds(worker.getId()));
        return new WorkerFacts(account.status(), account.workerActive(),
                WorkStatus.valueOf(worker.getWorkStatus()),
                AuditStatus.valueOf(worker.getAuditStatus()),
                serviceItemId, skills, required, usability, areas, serviceAreaId);
    }

    /**
     * 汇总每类证书的可用性。
     *
     * <p>同一类型可以有多张（旧证过期、新证待审）。<b>任意一张可用即可用</b>，
     * 否则换证期间人员会被误判为不可派单。都不可用时取"最容易处理"的那个原因：
     * 待审核 &lt; 已过期 &lt; 已驳回 &lt; 已撤销——运营看到"待审核"就知道只需等审核。</p>
     */
    private Map<CertificateType, CertificateUsability> collectCertificateUsability(long workerId) {
        Map<CertificateType, CertificateUsability> result = new EnumMap<>(CertificateType.class);
        for (WorkerCertificateEntity certificate : certificateMapper.selectByWorkerId(workerId)) {
            CertificateType type = CertificateType.of(certificate.getCertificateType());
            CertificateUsability usability = profileService.usabilityOf(certificate);
            CertificateUsability existing = result.get(type);
            if (existing == null || rank(usability) < rank(existing)) {
                result.put(type, usability);
            }
        }
        return result;
    }

    private int rank(CertificateUsability usability) {
        return switch (usability) {
            case USABLE -> 0;
            case PENDING_REVIEW -> 1;
            case EXPIRED -> 2;
            case REJECTED -> 3;
            case REVOKED -> 4;
        };
    }
}
