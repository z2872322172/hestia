package com.housekeeping.platform.workforce.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可派单资格判定（纯函数，docs/modules/05 第 9 节）。
 *
 * <p>核心原则：<b>账号激活不等于可派单</b>。人可能登录得好好的，但健康证过期、
 * 技能没审核、区域没配——任何一项不满足都直接排除，不进入评分排序。</p>
 *
 * <p>这里<b>不</b>校验"时间窗是否有空"和"是否超过每日接单量"：
 * 那两项依赖排班模块和订单模块的实时数据，属于派单时的动态条件；
 * 本判定只回答"这个人本身够不够格接这类活"。把静态资格和动态容量分开，
 * 才能让资格结果被缓存、被运营提前排查，而不是每次派单都全量重算。</p>
 *
 * <p>全不通过也不短路：一次把所有原因都列出来，运营改完一轮就能过，
 * 否则会陷入"改一个、试一次、再报下一个"的循环。</p>
 */
public final class EligibilityEvaluator {

    private static final String ACCOUNT_ACTIVE = "ACTIVE";

    private EligibilityEvaluator() {
    }

    /**
     * 判定人员是否具备派单资格。
     *
     * @param facts 判定事实
     * @return 判定结果
     */
    public static EligibilityResult evaluate(WorkerFacts facts) {
        EnumSet<EligibilityReason> reasons = EnumSet.noneOf(EligibilityReason.class);

        if (!ACCOUNT_ACTIVE.equals(facts.accountStatus())) {
            reasons.add(EligibilityReason.ACCOUNT_NOT_ACTIVE);
        }
        if (!facts.workerRoleActive()) {
            reasons.add(EligibilityReason.WORKER_ROLE_NOT_ACTIVE);
        }
        if (facts.workStatus() != WorkStatus.ACTIVE) {
            reasons.add(EligibilityReason.WORKER_NOT_ACTIVE);
        }
        if (facts.auditStatus() == null || !facts.auditStatus().isApproved()) {
            reasons.add(EligibilityReason.WORKER_AUDIT_NOT_APPROVED);
        }

        Set<Long> skills = facts.verifiedSkillItemIds() == null ? Set.of() : facts.verifiedSkillItemIds();
        if (!skills.contains(facts.serviceItemId())) {
            reasons.add(EligibilityReason.SKILL_NOT_VERIFIED);
        }

        checkCertificates(facts, reasons);

        Set<Long> areas = facts.activeServiceAreaIds() == null ? Set.of() : facts.activeServiceAreaIds();
        if (areas.isEmpty()) {
            reasons.add(EligibilityReason.SERVICE_AREA_NOT_CONFIGURED);
        } else if (facts.requiredServiceAreaId() != null
                && !areas.contains(facts.requiredServiceAreaId())) {
            reasons.add(EligibilityReason.SERVICE_AREA_NOT_COVERED);
        }

        if (reasons.isEmpty()) {
            return EligibilityResult.pass();
        }
        List<EligibilityReason> ordered = List.copyOf(reasons);
        return EligibilityResult.fail(ordered);
    }

    /**
     * 校验服务项目要求的证书。
     *
     * <p>"完全没有该类证书"与"有但不可用"给不同原因：前者要新交材料，
     * 后者可能只是等审核或换证，处理动作不同。</p>
     */
    private static void checkCertificates(WorkerFacts facts, EnumSet<EligibilityReason> reasons) {
        Set<CertificateType> required = facts.requiredCertificateTypes() == null
                ? Set.of() : facts.requiredCertificateTypes();
        Map<CertificateType, CertificateUsability> usability = facts.certificateUsability() == null
                ? Map.of() : facts.certificateUsability();
        for (CertificateType type : required) {
            CertificateUsability state = usability.get(type);
            if (state == null) {
                reasons.add(EligibilityReason.CERTIFICATE_REQUIRED);
            } else if (!state.usable()) {
                reasons.add(EligibilityReason.CERTIFICATE_EXPIRED);
            }
        }
    }
}
