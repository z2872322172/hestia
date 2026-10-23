package com.housekeeping.platform.workforce.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可派单资格判定测试（docs/modules/05 第 9 节）。
 *
 * <p>这是"账号激活不等于可派单"这条核心原则唯一的自动化守卫。
 * 每个 {@link EligibilityReason} 都有独立用例，任何一个被误删都会立刻红。</p>
 */
class EligibilityEvaluatorTest {

    private static final long ITEM_ID = 1001L;

    private static final long AREA_ID = 12L;

    @Test
    @DisplayName("全部条件满足 -> 可派单，原因列表为空")
    void eligibleWhenEverythingPasses() {
        EligibilityResult result = EligibilityEvaluator.evaluate(
                facts().certificate(CertificateType.HEALTH, CertificateUsability.USABLE).build());

        assertThat(result.eligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("账号未激活 -> ACCOUNT_NOT_ACTIVE")
    void accountNotActive() {
        assertThat(reasonsOf(facts().accountStatus("PENDING_ACTIVATION").build()))
                .containsExactly(EligibilityReason.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("WORKER 角色被暂停 -> WORKER_ROLE_NOT_ACTIVE")
    void workerRoleNotActive() {
        assertThat(reasonsOf(facts().workerRoleActive(false).build()))
                .containsExactly(EligibilityReason.WORKER_ROLE_NOT_ACTIVE);
    }

    @Test
    @DisplayName("人员暂停接单 -> WORKER_NOT_ACTIVE")
    void workerSuspended() {
        assertThat(reasonsOf(facts().workStatus(WorkStatus.SUSPENDED).build()))
                .containsExactly(EligibilityReason.WORKER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("档案未审核通过 -> WORKER_AUDIT_NOT_APPROVED")
    void auditPending() {
        assertThat(reasonsOf(facts().auditStatus(AuditStatus.PENDING).build()))
                .containsExactly(EligibilityReason.WORKER_AUDIT_NOT_APPROVED);
    }

    @Test
    @DisplayName("技能未审核覆盖该服务项目 -> SKILL_NOT_VERIFIED")
    void skillNotVerified() {
        assertThat(reasonsOf(facts().skills(Set.of(9999L)).build()))
                .containsExactly(EligibilityReason.SKILL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("要求健康证但一张都没有 -> CERTIFICATE_REQUIRED")
    void certificateMissing() {
        assertThat(reasonsOf(facts().requireCertificate(CertificateType.HEALTH).build()))
                .containsExactly(EligibilityReason.CERTIFICATE_REQUIRED);
    }

    @Test
    @DisplayName("有健康证但已过期 -> CERTIFICATE_EXPIRED")
    void certificateExpired() {
        assertThat(reasonsOf(facts().requireCertificate(CertificateType.HEALTH)
                .certificate(CertificateType.HEALTH, CertificateUsability.EXPIRED).build()))
                .containsExactly(EligibilityReason.CERTIFICATE_EXPIRED);
    }

    @Test
    @DisplayName("没有任何服务区域 -> SERVICE_AREA_NOT_CONFIGURED")
    void serviceAreaNotConfigured() {
        assertThat(reasonsOf(facts().areas(Set.of()).build()))
                .containsExactly(EligibilityReason.SERVICE_AREA_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("服务区域不覆盖订单区域 -> SERVICE_AREA_NOT_COVERED")
    void serviceAreaNotCovered() {
        assertThat(reasonsOf(facts().areas(Set.of(99L)).build()))
                .containsExactly(EligibilityReason.SERVICE_AREA_NOT_COVERED);
    }

    @Test
    @DisplayName("不指定订单区域时只校验\"有区域\"，不校验覆盖")
    void nullRequiredAreaSkipsCoverageCheck() {
        assertThat(reasonsOf(facts().areas(Set.of(99L)).requiredArea(null).build())).isEmpty();
    }

    @Test
    @DisplayName("多项不满足时一次返回全部原因，且按枚举声明顺序排列")
    void allReasonsReportedInDeclarationOrder() {
        EligibilityResult result = EligibilityEvaluator.evaluate(facts()
                .accountStatus("DISABLED")
                .workStatus(WorkStatus.SUSPENDED)
                .skills(Set.of())
                .requireCertificate(CertificateType.HEALTH)
                .areas(Set.of())
                .build());

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).containsExactly(
                EligibilityReason.ACCOUNT_NOT_ACTIVE,
                EligibilityReason.WORKER_NOT_ACTIVE,
                EligibilityReason.SKILL_NOT_VERIFIED,
                EligibilityReason.CERTIFICATE_REQUIRED,
                EligibilityReason.SERVICE_AREA_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("服务项目不要求证书时，证书检查不影响结论")
    void noRequiredCertificateIsVacuouslySatisfied() {
        assertThat(reasonsOf(facts().certificate(CertificateType.HEALTH,
                CertificateUsability.EXPIRED).build())).isEmpty();
    }

    @Test
    @DisplayName("用工类型不参与资格判定：兼职与全职用同一套规则")
    void employmentTypeIsNotPartOfEligibility() {
        // WorkerFacts 里根本没有用工类型字段，这条用例的价值是"提醒后来者不要加进去"：
        // 一旦有人往 facts 里塞 employmentType 再写 if (PART_TIME)，这条注释就是第一道拦阻。
        assertThat(reasonsOf(facts().build())).isEmpty();
    }

    private List<EligibilityReason> reasonsOf(WorkerFacts facts) {
        EligibilityResult result = EligibilityEvaluator.evaluate(facts);
        assertThat(result.eligible()).isEqualTo(result.reasons().isEmpty());
        return result.reasons();
    }

    private static FactsBuilder facts() {
        return new FactsBuilder();
    }

    /**
     * 事实构造器：默认值是"全部满足"，每个用例只改自己关心的那一项。
     */
    private static final class FactsBuilder {

        private String accountStatus = "ACTIVE";

        private boolean workerRoleActive = true;

        private WorkStatus workStatus = WorkStatus.ACTIVE;

        private AuditStatus auditStatus = AuditStatus.APPROVED;

        private Set<Long> skills = Set.of(ITEM_ID);

        private final Set<CertificateType> required = new java.util.LinkedHashSet<>();

        private final Map<CertificateType, CertificateUsability> certificates =
                new EnumMap<>(CertificateType.class);

        private Set<Long> areas = Set.of(AREA_ID);

        private Long requiredArea = AREA_ID;

        FactsBuilder accountStatus(String value) {
            this.accountStatus = value;
            return this;
        }

        FactsBuilder workerRoleActive(boolean value) {
            this.workerRoleActive = value;
            return this;
        }

        FactsBuilder workStatus(WorkStatus value) {
            this.workStatus = value;
            return this;
        }

        FactsBuilder auditStatus(AuditStatus value) {
            this.auditStatus = value;
            return this;
        }

        FactsBuilder skills(Set<Long> value) {
            this.skills = value;
            return this;
        }

        FactsBuilder requireCertificate(CertificateType type) {
            this.required.add(type);
            return this;
        }

        FactsBuilder certificate(CertificateType type, CertificateUsability usability) {
            this.certificates.put(type, usability);
            return this;
        }

        FactsBuilder areas(Set<Long> value) {
            this.areas = value;
            return this;
        }

        FactsBuilder requiredArea(Long value) {
            this.requiredArea = value;
            return this;
        }

        WorkerFacts build() {
            return new WorkerFacts(accountStatus, workerRoleActive, workStatus, auditStatus,
                    ITEM_ID, skills, Set.copyOf(required), Map.copyOf(certificates), areas, requiredArea);
        }
    }
}
