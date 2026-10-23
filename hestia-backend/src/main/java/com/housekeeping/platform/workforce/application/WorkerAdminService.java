package com.housekeeping.platform.workforce.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.view.ServiceItemRef;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.identity.application.AccountRoleService;
import com.housekeeping.platform.identity.application.InvitationService;
import com.housekeeping.platform.identity.application.contract.AccountRef;
import com.housekeeping.platform.identity.application.contract.AccountRefQueryService;
import com.housekeeping.platform.identity.application.view.InvitationView;
import com.housekeeping.platform.identity.domain.RoleCode;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.location.application.ServiceAreaQueryService;
import com.housekeeping.platform.workforce.application.command.CertificateReviewCommand;
import com.housekeeping.platform.workforce.application.command.CreateWorkerCommand;
import com.housekeeping.platform.workforce.application.command.SkillCommand;
import com.housekeeping.platform.workforce.application.command.UpdateWorkerCommand;
import com.housekeeping.platform.workforce.application.view.CertificateView;
import com.housekeeping.platform.workforce.application.view.CreatedWorkerView;
import com.housekeeping.platform.workforce.application.view.WorkerDetailView;
import com.housekeeping.platform.workforce.application.view.WorkerView;
import com.housekeeping.platform.workforce.domain.AuditStatus;
import com.housekeeping.platform.workforce.domain.CertificateAuditStatus;
import com.housekeeping.platform.workforce.domain.CertificateValidStatus;
import com.housekeeping.platform.workforce.domain.SkillLevel;
import com.housekeeping.platform.workforce.domain.WorkStatus;
import com.housekeeping.platform.workforce.persistence.entity.WorkerCertificateEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerProfileEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerServiceAreaEntity;
import com.housekeeping.platform.workforce.persistence.entity.WorkerSkillEntity;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerCertificateMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerProfileMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerServiceAreaMapper;
import com.housekeeping.platform.workforce.persistence.mapper.WorkerSkillMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 人员档案管理（运营端）。
 *
 * <p>状态流转一律走"条件更新"：{@code UPDATE ... WHERE work_status = 期望值}，
 * 影响行数为 0 就是并发冲突。先读再写的写法在两个人同时点"暂停"和"离职"时，
 * 会把离职的人悄悄改回暂停——这类问题在测试环境永远复现不出来。</p>
 *
 * <p>暂停与离职都要联动身份模块：暂停把 WORKER 角色置为 {@code SUSPENDED} 并递增
 * {@code token_version}（旧令牌里还写着 {@code role=WORKER}），离职则撤销角色。
 * 只改人员表而不动角色，人员仍能登录并调用 {@code /api/v1/worker/**}。</p>
 */
@Service
@RequiresPersistence
public class WorkerAdminService {

    private static final String WORKER_NO_SEQ_KEY = "WORKER";

    private static final String WORKER_NO_PREFIX = "WK";

    private final WorkerProfileMapper profileMapper;

    private final WorkerSkillMapper skillMapper;

    private final WorkerCertificateMapper certificateMapper;

    private final WorkerServiceAreaMapper serviceAreaMapper;

    private final WorkerProfileService profileService;

    private final InvitationService invitationService;

    private final AccountRoleService accountRoleService;

    private final AccountRefQueryService accountRefQueryService;

    private final ServiceCatalogQueryService catalogQueryService;

    private final ServiceAreaQueryService serviceAreaQueryService;

    private final BusinessNoGenerator businessNoGenerator;

    public WorkerAdminService(WorkerProfileMapper profileMapper,
                              WorkerSkillMapper skillMapper,
                              WorkerCertificateMapper certificateMapper,
                              WorkerServiceAreaMapper serviceAreaMapper,
                              WorkerProfileService profileService,
                              InvitationService invitationService,
                              AccountRoleService accountRoleService,
                              AccountRefQueryService accountRefQueryService,
                              ServiceCatalogQueryService catalogQueryService,
                              ServiceAreaQueryService serviceAreaQueryService,
                              BusinessNoGenerator businessNoGenerator) {
        this.profileMapper = profileMapper;
        this.skillMapper = skillMapper;
        this.certificateMapper = certificateMapper;
        this.serviceAreaMapper = serviceAreaMapper;
        this.profileService = profileService;
        this.invitationService = invitationService;
        this.accountRoleService = accountRoleService;
        this.accountRefQueryService = accountRefQueryService;
        this.catalogQueryService = catalogQueryService;
        this.serviceAreaQueryService = serviceAreaQueryService;
        this.businessNoGenerator = businessNoGenerator;
    }

    /**
     * 创建人员档案并开户。
     *
     * <p>开户交给身份模块的邀请流程，本方法只负责"建档案"。这样同一手机号
     * 如果已经是消费者，身份模块会复用账号并追加 WORKER 角色，而不是新建第二个账号——
     * 否则同一个人的历史订单会分裂到两个身份上。</p>
     *
     * @param command    创建命令
     * @param operatorId 操作人账号 ID
     * @return 创建结果（含一次性激活凭证）
     */
    @Transactional
    public CreatedWorkerView create(CreateWorkerCommand command, long operatorId) {
        if (command.realName() == null || command.realName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "真实姓名不能为空");
        }
        InvitationView invitation = invitationService.createWorkerInvitation(
                command.countryCode(), command.phone(), command.displayName(), operatorId);
        AccountRef account = accountRefQueryService.requireRefByAccountNo(invitation.accountNo());
        if (profileMapper.selectByAccountId(account.accountId()) != null) {
            throw new BusinessException(ErrorCode.WORKER_ACCOUNT_ALREADY_BOUND);
        }

        WorkerProfileEntity entity = new WorkerProfileEntity();
        entity.setWorkerNo(businessNoGenerator.next(WORKER_NO_SEQ_KEY, WORKER_NO_PREFIX));
        entity.setAccountId(account.accountId());
        entity.setRealName(command.realName().trim());
        entity.setGender(profileService.normalizeGender(command.gender()));
        entity.setEmploymentType(profileService.normalizeEmploymentType(command.employmentType()));
        entity.setWorkStatus(WorkStatus.ONBOARDING.name());
        entity.setAuditStatus(AuditStatus.PENDING.name());
        entity.setExperienceYears(profileService.normalizeExperienceYears(command.experienceYears()));
        entity.setIntroduction(command.introduction());
        entity.setMaxDailyOrders(profileService.normalizeMaxDailyOrders(command.maxDailyOrders()));
        try {
            profileMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 唯一索引兜底：并发创建同一账号的档案
            throw new BusinessException(ErrorCode.WORKER_ACCOUNT_ALREADY_BOUND);
        }
        return new CreatedWorkerView(entity.getWorkerNo(), account.accountNo(),
                invitation.activationToken(), invitation.expiresAt(),
                invitation.activationRequired(), invitation.message());
    }

    /**
     * 人员分页（运营端）。
     *
     * @param page        页码
     * @param size        单页条数
     * @param keyword     姓名或编号关键字
     * @param workStatus  工作状态
     * @param auditStatus 审核状态
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<WorkerView> page(int page, int size, String keyword,
                                       String workStatus, String auditStatus) {
        LambdaQueryWrapper<WorkerProfileEntity> wrapper =
                profileService.buildListWrapper(keyword, workStatus, auditStatus);
        Page<WorkerProfileEntity> result = profileMapper.selectPage(new Page<>(page, size), wrapper);
        List<WorkerView> records = result.getRecords().stream().map(profileService::toView).toList();
        return PageResult.of(records, page, size, result.getTotal());
    }

    /**
     * 人员详情（运营端，含账号编号）。
     *
     * @param workerNo 人员编号
     * @return 详情
     */
    @Transactional(readOnly = true)
    public WorkerDetailView detail(String workerNo) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        AccountRef account = accountRefQueryService.requireRefById(worker.getAccountId());
        WorkerDetailView base = profileService.assembleDetail(worker);
        return new WorkerDetailView(base.workerNo(), account.accountNo(), base.realName(), base.gender(),
                base.employmentType(), base.workStatus(), base.auditStatus(), base.experienceYears(),
                base.introduction(), base.maxDailyOrders(), base.rating(), base.completedOrderCount(),
                base.joinedAt(), base.leftAt(), base.skills(), base.certificates(), base.serviceAreas());
    }

    /**
     * 修改人员档案（运营端）。
     *
     * @param workerNo   人员编号
     * @param command    修改命令
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void update(String workerNo, UpdateWorkerCommand command, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        profileService.assertNotLeft(worker);
        profileService.assertVersion(worker, command.expectedVersion());

        worker.setRealName(command.realName() == null || command.realName().isBlank()
                ? worker.getRealName() : command.realName().trim());
        worker.setGender(profileService.normalizeGender(command.gender()));
        worker.setEmploymentType(profileService.normalizeEmploymentType(command.employmentType()));
        worker.setExperienceYears(profileService.normalizeExperienceYears(command.experienceYears()));
        worker.setIntroduction(command.introduction());
        worker.setMaxDailyOrders(profileService.normalizeMaxDailyOrders(command.maxDailyOrders()));
        worker.setUpdatedByAccountId(operatorId);
        if (profileMapper.updateById(worker) == 0) {
            throw new BusinessException(ErrorCode.WORKER_VERSION_CONFLICT);
        }
    }

    /**
     * 审核通过并办理入职。
     *
     * <p>审核通过同时把 {@code ONBOARDING} 推进到 {@code ACTIVE}：首版里"审核"与"入职"
     * 是同一个动作（小公司没有独立的入职流程）。如果后续需要分开，再加一个
     * {@code /activate} 接口，而不是让审核接口悄悄只改一个字段。</p>
     *
     * @param workerNo   人员编号
     * @param operatorId 操作人账号 ID
     * @return 最新详情
     */
    @Transactional
    public WorkerDetailView approve(String workerNo, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        if (AuditStatus.APPROVED.name().equals(worker.getAuditStatus())) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT, "该人员已审核通过");
        }
        if (profileMapper.updateAuditStatus(worker.getId(), worker.getAuditStatus(),
                AuditStatus.APPROVED.name(), operatorId) == 0) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT);
        }
        if (WorkStatus.ONBOARDING.name().equals(worker.getWorkStatus())) {
            if (profileMapper.updateWorkStatus(worker.getId(), WorkStatus.ONBOARDING.name(),
                    WorkStatus.ACTIVE.name(), null, operatorId) == 0) {
                throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT);
            }
            profileMapper.markJoined(worker.getId(), LocalDateTime.now());
        }
        return detail(workerNo);
    }

    /**
     * 暂停接单。
     *
     * <p>同时把 WORKER 角色置为 {@code SUSPENDED} 并递增 {@code token_version}：
     * 只改人员表的话，他手上的旧令牌仍然带着 {@code role=WORKER}，
     * 在令牌过期前依旧能访问服务人员接口。</p>
     *
     * @param workerNo   人员编号
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void suspend(String workerNo, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        if (!WorkStatus.ACTIVE.name().equals(worker.getWorkStatus())) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT, "只有在岗人员可以暂停");
        }
        if (profileMapper.updateWorkStatus(worker.getId(), WorkStatus.ACTIVE.name(),
                WorkStatus.SUSPENDED.name(), null, operatorId) == 0) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT);
        }
        accountRoleService.suspend(worker.getAccountId(), RoleCode.WORKER);
    }

    /**
     * 恢复接单。
     *
     * <p>恢复前必须复核审核状态：档案被驳回的人不能靠"恢复"绕过审核。</p>
     *
     * @param workerNo   人员编号
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void reactivate(String workerNo, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        if (WorkStatus.LEFT.name().equals(worker.getWorkStatus())) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT, "已离职人员不可恢复");
        }
        if (!WorkStatus.SUSPENDED.name().equals(worker.getWorkStatus())) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT, "只有在暂停状态可以恢复");
        }
        if (!AuditStatus.APPROVED.name().equals(worker.getAuditStatus())) {
            throw new BusinessException(ErrorCode.WORKER_AUDIT_NOT_APPROVED);
        }
        if (profileMapper.updateWorkStatus(worker.getId(), WorkStatus.SUSPENDED.name(),
                WorkStatus.ACTIVE.name(), null, operatorId) == 0) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT);
        }
        accountRoleService.grant(worker.getAccountId(), RoleCode.WORKER, operatorId);
    }

    /**
     * 办理离职（终态）。
     *
     * <p>撤销 WORKER 角色而不是暂停：离职后不再有"恢复"路径，
     * 留着 SUSPENDED 的角色只会让后续排查误以为这人随时能回来。
     * 未完成订单的改派属于派单模块职责（批次 4），这里只做状态与角色。</p>
     *
     * @param workerNo   人员编号
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void leave(String workerNo, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        WorkStatus current = WorkStatus.valueOf(worker.getWorkStatus());
        if (current == WorkStatus.LEFT) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT, "该人员已离职");
        }
        if (!current.canTransitionTo(WorkStatus.LEFT)) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT);
        }
        if (profileMapper.updateWorkStatus(worker.getId(), current.name(), WorkStatus.LEFT.name(),
                LocalDateTime.now(), operatorId) == 0) {
            throw new BusinessException(ErrorCode.WORKER_STATUS_CONFLICT);
        }
        accountRoleService.revoke(worker.getAccountId(), RoleCode.WORKER);
    }

    /**
     * 整体替换人员技能（运营端授权 + 审核一步完成）。
     *
     * <p>整体替换而不是增删单项：技能集合很小，且"同一服务项目不能有两条技能"
     * 由唯一索引保证。做增量接口反而要处理"先删后加"的中间态。</p>
     *
     * @param workerNo   人员编号
     * @param commands   技能列表
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void replaceSkills(String workerNo, List<SkillCommand> commands, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        profileService.assertNotLeft(worker);
        Set<Long> seen = new HashSet<>();
        skillMapper.deleteByWorkerId(worker.getId());
        if (commands == null) {
            return;
        }
        for (SkillCommand command : commands) {
            ServiceItemRef item = catalogQueryService.requireItemRef(command.serviceItemNo());
            if (!seen.add(item.serviceItemId())) {
                throw new BusinessException(ErrorCode.WORKER_SKILL_INVALID, "同一服务项目不能重复授权");
            }
            WorkerSkillEntity entity = new WorkerSkillEntity();
            entity.setWorkerId(worker.getId());
            entity.setServiceItemId(item.serviceItemId());
            entity.setSkillLevel(parseSkillLevel(command.skillLevel()).name());
            entity.setVerified(command.verified());
            entity.setStatus("ACTIVE");
            if (command.verified()) {
                entity.setVerifiedByAccountId(operatorId);
                entity.setVerifiedAt(LocalDateTime.now());
            }
            skillMapper.insert(entity);
        }
    }

    /**
     * 整体替换人员服务区域（运营端）。
     *
     * <p>只接受 {@code ACTIVE} 的片区：归档片区不能再分配给新人员，
     * 否则"归档"就失去了意义。</p>
     *
     * @param workerNo   人员编号
     * @param areaIds    服务区域 ID 列表
     * @param operatorId 操作人账号 ID
     */
    @Transactional
    public void replaceServiceAreas(String workerNo, List<Long> areaIds, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        profileService.assertNotLeft(worker);
        serviceAreaMapper.deleteByWorkerId(worker.getId());
        if (areaIds == null) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Long areaId : areaIds) {
            if (areaId == null || !seen.add(areaId)) {
                throw new BusinessException(ErrorCode.WORKER_SERVICE_AREA_INVALID, "服务区域重复");
            }
            try {
                serviceAreaQueryService.requireActive(areaId);
            } catch (BusinessException exception) {
                throw new BusinessException(ErrorCode.WORKER_SERVICE_AREA_INVALID, exception.getMessage());
            }
            WorkerServiceAreaEntity entity = new WorkerServiceAreaEntity();
            entity.setWorkerId(worker.getId());
            entity.setServiceAreaId(areaId);
            entity.setStatus("ACTIVE");
            entity.setUpdatedByAccountId(operatorId);
            serviceAreaMapper.insert(entity);
        }
    }

    /**
     * 审核证书。
     *
     * @param workerNo    人员编号
     * @param certificateId 证书 ID
     * @param command     审核命令
     * @param operatorId  操作人账号 ID
     * @return 审核后的证书视图
     */
    @Transactional
    public CertificateView reviewCertificate(String workerNo, long certificateId,
                                             CertificateReviewCommand command, long operatorId) {
        WorkerProfileEntity worker = profileService.requireByWorkerNo(workerNo);
        WorkerCertificateEntity certificate = certificateMapper.selectById(certificateId);
        if (certificate == null || !worker.getId().equals(certificate.getWorkerId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "证书不存在");
        }
        CertificateAuditStatus audit = parseCertificateAudit(command.auditStatus());
        CertificateValidStatus valid = command.validStatus() == null || command.validStatus().isBlank()
                ? CertificateValidStatus.valueOf(certificate.getValidStatus())
                : parseCertificateValid(command.validStatus());

        LambdaUpdateWrapper<WorkerCertificateEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WorkerCertificateEntity::getId, certificateId)
                .set(WorkerCertificateEntity::getAuditStatus, audit.name())
                .set(WorkerCertificateEntity::getValidStatus, valid.name())
                .set(WorkerCertificateEntity::getRemark,
                        command.remark() == null ? certificate.getRemark() : command.remark())
                .set(WorkerCertificateEntity::getUpdatedByAccountId, operatorId);
        if (audit == CertificateAuditStatus.APPROVED) {
            wrapper.set(WorkerCertificateEntity::getVerifiedByAccountId, operatorId)
                    .set(WorkerCertificateEntity::getVerifiedAt, LocalDateTime.now());
        }
        if (certificateMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ErrorCode.WORKER_CERTIFICATE_VERSION_CONFLICT);
        }
        return profileService.toCertificateView(certificateMapper.selectById(certificateId));
    }

    private SkillLevel parseSkillLevel(String value) {
        try {
            return SkillLevel.of(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
    }

    private CertificateAuditStatus parseCertificateAudit(String value) {
        try {
            return CertificateAuditStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.WORKER_CERTIFICATE_INVALID, "审核状态非法：" + value);
        }
    }

    private CertificateValidStatus parseCertificateValid(String value) {
        try {
            return CertificateValidStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.WORKER_CERTIFICATE_INVALID, "有效性状态非法：" + value);
        }
    }
}
