package com.housekeeping.platform.workforce.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.view.ServiceItemRef;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.security.FieldCipher;
import com.housekeeping.platform.workforce.application.command.CertificateCommand;
import com.housekeeping.platform.workforce.application.command.UpdateWorkerCommand;
import com.housekeeping.platform.workforce.application.view.CertificateView;
import com.housekeeping.platform.workforce.application.view.SkillView;
import com.housekeeping.platform.workforce.application.view.WorkerDetailView;
import com.housekeeping.platform.workforce.application.view.WorkerServiceAreaView;
import com.housekeeping.platform.workforce.application.view.WorkerView;
import com.housekeeping.platform.workforce.domain.AuditStatus;
import com.housekeeping.platform.workforce.domain.CertificateAuditStatus;
import com.housekeeping.platform.workforce.domain.CertificateType;
import com.housekeeping.platform.workforce.domain.CertificateUsability;
import com.housekeeping.platform.workforce.domain.CertificateValidStatus;
import com.housekeeping.platform.workforce.domain.CertificateValidity;
import com.housekeeping.platform.workforce.domain.EmploymentType;
import com.housekeeping.platform.workforce.domain.WorkStatus;
import com.housekeeping.platform.workforce.persistence.entity.WorkerCertificateEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerProfileEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerServiceAreaEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerSkillEntity;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerCertificateMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerProfileMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerServiceAreaMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerSkillMapper;
import com.housekeeping.platform.location.application.ServiceAreaQueryService;
import com.housekeeping.platform.location.persistence.entity.ServiceAreaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务人员档案服务（本人视角 + 公共查询）。
 *
 * <p>本人能改的只有"非审核基础信息"：性别、自我介绍。姓名、用工类型、接单上限
 * 都由运营维护——这些字段一旦本人可改，就出现了"自己给自己放宽接单上限"的路径。
 * 技能与证书的<b>审核结论</b>本人更不能改，只能提交材料。</p>
 */
@Service
@RequiresPersistence
public class WorkerProfileService {

    private static final int DEFAULT_MAX_DAILY_ORDERS = 4;

    private final WorkerProfileMapper profileMapper;

    private final WorkerSkillMapper skillMapper;

    private final WorkerCertificateMapper certificateMapper;

    private final WorkerServiceAreaMapper serviceAreaMapper;

    private final ServiceCatalogQueryService catalogQueryService;

    private final ServiceAreaQueryService serviceAreaQueryService;

    private final FieldCipher fieldCipher;

    public WorkerProfileService(WorkerProfileMapper profileMapper,
                                WorkerSkillMapper skillMapper,
                                WorkerCertificateMapper certificateMapper,
                                WorkerServiceAreaMapper serviceAreaMapper,
                                ServiceCatalogQueryService catalogQueryService,
                                ServiceAreaQueryService serviceAreaQueryService,
                                FieldCipher fieldCipher) {
        this.profileMapper = profileMapper;
        this.skillMapper = skillMapper;
        this.certificateMapper = certificateMapper;
        this.serviceAreaMapper = serviceAreaMapper;
        this.catalogQueryService = catalogQueryService;
        this.serviceAreaQueryService = serviceAreaQueryService;
        this.fieldCipher = fieldCipher;
    }

    /**
     * 按账号取人员档案。
     *
     * @param accountId 账号 ID
     * @return 档案实体
     * @throws BusinessException 该账号没有人员档案
     */
    @Transactional(readOnly = true)
    public WorkerProfileEntity requireByAccountId(long accountId) {
        WorkerProfileEntity entity = profileMapper.selectByAccountId(accountId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.WORKER_PROFILE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 按人员编号取人员档案。
     *
     * @param workerNo 人员编号
     * @return 档案实体
     * @throws BusinessException 档案不存在
     */
    @Transactional(readOnly = true)
    public WorkerProfileEntity requireByWorkerNo(String workerNo) {
        WorkerProfileEntity entity = profileMapper.selectByWorkerNo(workerNo);
        if (entity == null) {
            throw new BusinessException(ErrorCode.WORKER_PROFILE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 按 ID 取人员档案。
     *
     * @param workerId 人员 ID
     * @return 档案实体
     * @throws BusinessException 档案不存在
     */
    @Transactional(readOnly = true)
    public WorkerProfileEntity requireById(long workerId) {
        WorkerProfileEntity entity = profileMapper.selectById(workerId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.WORKER_PROFILE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 批量查询人员的每日接单上限（scheduling 可用性过滤使用）。
     *
     * <p>只取 id 与上限两列：候选人员集合小，但逐个 {@code requireById} 会放大查询次数。</p>
     *
     * @param workerIds 人员 ID 集合
     * @return workerId → maxDailyOrders（未配置的按 0 处理，由调用方决定语义）
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> maxDailyOrdersByIds(Collection<Long> workerIds) {
        if (workerIds == null || workerIds.isEmpty()) {
            return Map.of();
        }
        List<WorkerProfileEntity> entities = profileMapper.selectList(
                new LambdaQueryWrapper<WorkerProfileEntity>()
                        .in(WorkerProfileEntity::getId, workerIds)
                        .select(WorkerProfileEntity::getId, WorkerProfileEntity::getMaxDailyOrders));
        Map<Long, Integer> result = new HashMap<>(entities.size());
        for (WorkerProfileEntity entity : entities) {
            result.put(entity.getId(), entity.getMaxDailyOrders() == null ? 0 : entity.getMaxDailyOrders());
        }
        return result;
    }

    /**
     * 本人的档案摘要。
     *
     * @param accountId 当前账号 ID
     * @return 档案摘要
     */
    @Transactional(readOnly = true)
    public WorkerView myProfile(long accountId) {
        return toView(requireByAccountId(accountId));
    }

    /**
     * 本人的完整档案（含技能、证书、区域）。
     *
     * @param accountId 当前账号 ID
     * @return 档案详情
     */
    @Transactional(readOnly = true)
    public WorkerDetailView myDetail(long accountId) {
        return assembleDetail(requireByAccountId(accountId));
    }

    /**
     * 修改本人非审核基础信息。
     *
     * <p>只接受性别与自我介绍，其余字段一律忽略——不是"暂时没实现"，
     * 而是这些字段本来就不该由本人改。</p>
     *
     * @param accountId 当前账号 ID
     * @param command   修改命令
     */
    @Transactional
    public void updateMyProfile(long accountId, UpdateWorkerCommand command) {
        WorkerProfileEntity entity = requireByAccountId(accountId);
        assertVersion(entity, command.expectedVersion());
        assertNotLeft(entity);
        entity.setGender(normalizeGender(command.gender()));
        entity.setIntroduction(command.introduction());
        if (profileMapper.updateById(entity) == 0) {
            throw new BusinessException(ErrorCode.WORKER_VERSION_CONFLICT);
        }
    }

    /**
     * 本人提交证书。
     *
     * <p>提交后一律是 {@code PENDING}：本人提交的证书不可能自己就是"已审核"，
     * 这是"不能自行审核技能或证书"这条规则唯一的落点。</p>
     *
     * @param accountId 当前账号 ID
     * @param command   证书信息
     * @return 证书视图
     */
    @Transactional
    public CertificateView addCertificate(long accountId, CertificateCommand command) {
        WorkerProfileEntity worker = requireByAccountId(accountId);
        assertNotLeft(worker);

        CertificateType type = parseCertificateType(command.certificateType());
        if (command.issuedAt() != null && command.expiresAt() != null
                && command.expiresAt().isBefore(command.issuedAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "到期日不能早于发证日");
        }

        WorkerCertificateEntity entity = new WorkerCertificateEntity();
        entity.setWorkerId(worker.getId());
        entity.setCertificateType(type.name());
        entity.setFileId(command.fileId());
        entity.setIssuedAt(command.issuedAt());
        entity.setExpiresAt(command.expiresAt());
        entity.setAuditStatus(CertificateAuditStatus.PENDING.name());
        entity.setValidStatus(CertificateValidStatus.VALID.name());
        entity.setRemark(command.remark());
        applyCertificateNo(entity, command.certificateNo());
        certificateMapper.insert(entity);
        return toCertificateView(entity);
    }

    /**
     * 本人的证书列表。
     *
     * @param accountId 当前账号 ID
     * @return 证书列表
     */
    @Transactional(readOnly = true)
    public List<CertificateView> myCertificates(long accountId) {
        WorkerProfileEntity worker = requireByAccountId(accountId);
        return certificateMapper.selectByWorkerId(worker.getId()).stream()
                .map(this::toCertificateView)
                .toList();
    }

    /**
     * 本人的技能列表。
     *
     * @param accountId 当前账号 ID
     * @return 技能列表
     */
    @Transactional(readOnly = true)
    public List<SkillView> mySkills(long accountId) {
        WorkerProfileEntity worker = requireByAccountId(accountId);
        return toSkillViews(skillMapper.selectByWorkerId(worker.getId()));
    }

    /**
     * 本人的服务区域。
     *
     * @param accountId 当前账号 ID
     * @return 区域列表
     */
    @Transactional(readOnly = true)
    public List<WorkerServiceAreaView> myServiceAreas(long accountId) {
        WorkerProfileEntity worker = requireByAccountId(accountId);
        return toAreaViews(serviceAreaMapper.selectByWorkerId(worker.getId()));
    }

    /**
     * 组装人员详情（运营与本人共用）。
     *
     * @param entity 档案实体
     * @return 详情视图
     */
    @Transactional(readOnly = true)
    public WorkerDetailView assembleDetail(WorkerProfileEntity entity) {
        return new WorkerDetailView(entity.getWorkerNo(), null, entity.getRealName(),
                entity.getGender(), entity.getEmploymentType(), entity.getWorkStatus(),
                entity.getAuditStatus(), entity.getExperienceYears(), entity.getIntroduction(),
                entity.getMaxDailyOrders() == null ? DEFAULT_MAX_DAILY_ORDERS : entity.getMaxDailyOrders(),
                entity.getRating(), entity.getCompletedOrderCount() == null ? 0 : entity.getCompletedOrderCount(),
                entity.getJoinedAt(), entity.getLeftAt(),
                toSkillViews(skillMapper.selectByWorkerId(entity.getId())),
                certificateMapper.selectByWorkerId(entity.getId()).stream()
                        .map(this::toCertificateView).toList(),
                toAreaViews(serviceAreaMapper.selectByWorkerId(entity.getId())));
    }

    /**
     * 实体转摘要视图。
     *
     * @param entity 档案实体
     * @return 摘要视图
     */
    public WorkerView toView(WorkerProfileEntity entity) {
        return new WorkerView(entity.getWorkerNo(), entity.getRealName(), entity.getEmploymentType(),
                entity.getWorkStatus(), entity.getAuditStatus(), entity.getExperienceYears(),
                entity.getMaxDailyOrders() == null ? DEFAULT_MAX_DAILY_ORDERS : entity.getMaxDailyOrders(),
                entity.getRating(),
                entity.getCompletedOrderCount() == null ? 0 : entity.getCompletedOrderCount(),
                entity.getJoinedAt());
    }

    /**
     * 证书实体转视图（现算可用性）。
     *
     * @param entity 证书实体
     * @return 证书视图
     */
    public CertificateView toCertificateView(WorkerCertificateEntity entity) {
        CertificateUsability usability = usabilityOf(entity);
        return new CertificateView(entity.getId(), entity.getCertificateType(), entity.getIssuedAt(),
                entity.getExpiresAt(), entity.getAuditStatus(), entity.getValidStatus(),
                usability.usable(), usability.name(), entity.getVerifiedAt(), entity.getRemark());
    }

    /**
     * 判定证书当前可用性。
     *
     * <p>视图与资格校验共用这一个入口，避免"详情页显示可用、派单却说不可用"。</p>
     *
     * @param entity 证书实体
     * @return 可用性
     */
    public CertificateUsability usabilityOf(WorkerCertificateEntity entity) {
        return CertificateValidity.evaluate(
                parseEnum(CertificateAuditStatus.class, entity.getAuditStatus()),
                parseEnum(CertificateValidStatus.class, entity.getValidStatus()),
                entity.getExpiresAt(), LocalDate.now());
    }

    /**
     * 写入证书编号：密文入库 + HMAC 摘要查重。
     *
     * <p>编号为空时两列都留空。密钥未配置时直接拒绝，而不是退化成明文——
     * 明文落库后再想改回密文，得先把已经泄漏的部分当泄漏处理。</p>
     */
    private void applyCertificateNo(WorkerCertificateEntity entity, String certificateNo) {
        if (certificateNo == null || certificateNo.isBlank()) {
            return;
        }
        if (!fieldCipher.isConfigured()) {
            throw new BusinessException(ErrorCode.WORKER_CERTIFICATE_CIPHER_UNAVAILABLE);
        }
        String normalized = certificateNo.trim().toUpperCase();
        entity.setCertificateNoCipher(fieldCipher.encrypt(normalized));
        entity.setCertificateNoHash(fieldCipher.hmac(normalized));
    }

    private List<SkillView> toSkillViews(List<WorkerSkillEntity> skills) {
        List<SkillView> views = new ArrayList<>(skills.size());
        for (WorkerSkillEntity skill : skills) {
            // 技能条数有上限（每人最多覆盖几个服务项目），这里逐个取名称是 N 次主键查询，
            // 换成分页或批量映射的复杂度不值得——真到需要优化时，证据是慢查询日志而不是猜测。
            ServiceItemRef item = catalogQueryService.requireItemRefById(skill.getServiceItemId());
            views.add(new SkillView(item.serviceItemNo(), item.serviceItemName(), skill.getSkillLevel(),
                    Boolean.TRUE.equals(skill.getVerified()), skill.getStatus(), skill.getVerifiedAt()));
        }
        return views;
    }

    private List<WorkerServiceAreaView> toAreaViews(List<WorkerServiceAreaEntity> relations) {
        List<WorkerServiceAreaView> views = new ArrayList<>(relations.size());
        for (WorkerServiceAreaEntity relation : relations) {
            ServiceAreaEntity area = serviceAreaQueryService.requireById(relation.getServiceAreaId());
            views.add(new WorkerServiceAreaView(area.getId(), area.getAreaNo(), area.getAreaName(),
                    relation.getStatus()));
        }
        return views;
    }

    /**
     * 校验乐观锁版本。
     *
     * @param entity          档案实体
     * @param expectedVersion 客户端版本
     */
    public void assertVersion(WorkerProfileEntity entity, long expectedVersion) {
        long actual = entity.getVersion() == null ? 0L : entity.getVersion();
        if (actual != expectedVersion) {
            throw new BusinessException(ErrorCode.WORKER_VERSION_CONFLICT);
        }
    }

    /**
     * 离职人员不允许再改档案。
     *
     * @param entity 档案实体
     */
    public void assertNotLeft(WorkerProfileEntity entity) {
        if (WorkStatus.LEFT.name().equals(entity.getWorkStatus())) {
            throw new BusinessException(ErrorCode.WORKER_NOT_ACTIVE, "人员已离职，档案不可修改");
        }
    }

    /**
     * 校验用工类型合法性。
     *
     * @param value 原始值
     * @return 归一化后的枚举名
     */
    public String normalizeEmploymentType(String value) {
        try {
            return EmploymentType.of(value).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
    }

    /**
     * 校验性别取值。
     *
     * @param value 原始值
     * @return 归一化后的值，空返回 {@code null}
     */
    public String normalizeGender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!List.of("MALE", "FEMALE", "OTHER").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "性别取值非法：" + value);
        }
        return normalized;
    }

    /**
     * 校验审核状态取值。
     *
     * @param value 原始值
     * @return 枚举
     */
    public AuditStatus parseAuditStatus(String value) {
        return parseEnum(AuditStatus.class, value);
    }

    /**
     * 校验证书类型取值。
     *
     * @param value 原始值
     * @return 枚举
     */
    public CertificateType parseCertificateType(String value) {
        try {
            return CertificateType.of(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "状态值不能为空");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "状态取值非法：" + value);
        }
    }

    /**
     * 构造"按条件查档案"的查询条件（运营列表用）。
     *
     * @param keyword    姓名或编号关键字，可空
     * @param workStatus 工作状态，可空
     * @param auditStatus 审核状态，可空
     * @return 查询条件
     */
    public LambdaQueryWrapper<WorkerProfileEntity> buildListWrapper(String keyword,
                                                                    String workStatus,
                                                                    String auditStatus) {
        LambdaQueryWrapper<WorkerProfileEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(WorkerProfileEntity::getRealName, trimmed)
                    .or().like(WorkerProfileEntity::getWorkerNo, trimmed));
        }
        if (workStatus != null && !workStatus.isBlank()) {
            wrapper.eq(WorkerProfileEntity::getWorkStatus, workStatus.trim().toUpperCase());
        }
        if (auditStatus != null && !auditStatus.isBlank()) {
            wrapper.eq(WorkerProfileEntity::getAuditStatus, auditStatus.trim().toUpperCase());
        }
        wrapper.orderByDesc(WorkerProfileEntity::getId);
        return wrapper;
    }

    /**
     * 归一化从业年限，负数视为非法。
     *
     * @param value 原始值
     * @return 非负数值，空返回 0
     */
    public BigDecimal normalizeExperienceYears(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "从业年限不能为负");
        }
        return value;
    }

    /**
     * 归一化每日最大接单量。
     *
     * @param value 原始值
     * @return 1..20 之间的值，空返回默认值
     */
    public int normalizeMaxDailyOrders(Integer value) {
        if (value == null) {
            return DEFAULT_MAX_DAILY_ORDERS;
        }
        if (value < 1 || value > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "每日最大接单量应在 1~20 之间");
        }
        return value;
    }
}
