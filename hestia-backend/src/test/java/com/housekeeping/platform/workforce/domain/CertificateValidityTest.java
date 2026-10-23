package com.housekeeping.platform.workforce.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证书可用性测试（docs/modules/05 第 5.3、10 节）。
 *
 * <p>全部用固定日期，不取系统时间：到期判定涉及"含当天"的语义，
 * 用 {@code LocalDate.now()} 写测试在跨天时会出现"昨天绿的今天红了"。</p>
 */
class CertificateValidityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    @DisplayName("已审核且未到期 -> 可用")
    void approvedAndNotExpired() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.APPROVED,
                CertificateValidStatus.VALID, LocalDate.of(2027, 1, 1), TODAY))
                .isEqualTo(CertificateUsability.USABLE);
    }

    @Test
    @DisplayName("到期日当天仍然可用：\"有效期至 10-23\" 包含 10-23")
    void expiresTodayIsStillUsable() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.APPROVED,
                CertificateValidStatus.VALID, TODAY, TODAY))
                .isEqualTo(CertificateUsability.USABLE);
    }

    @Test
    @DisplayName("到期日的次日 -> 过期")
    void expiredTheDayAfter() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.APPROVED,
                CertificateValidStatus.VALID, TODAY.minusDays(1), TODAY))
                .isEqualTo(CertificateUsability.EXPIRED);
    }

    @Test
    @DisplayName("长期有效（expiresAt 为空）不会因日期被判过期")
    void nullExpiryNeverExpires() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.APPROVED,
                CertificateValidStatus.VALID, null, TODAY))
                .isEqualTo(CertificateUsability.USABLE);
    }

    @Test
    @DisplayName("待审核 -> 不可用，但原因是待审核而不是过期")
    void pendingReview() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.PENDING,
                CertificateValidStatus.VALID, LocalDate.of(2027, 1, 1), TODAY))
                .isEqualTo(CertificateUsability.PENDING_REVIEW);
    }

    @Test
    @DisplayName("已撤销优先于过期：撤销意味着材料不可信，日期不再有意义")
    void revokedBeatsExpired() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.APPROVED,
                CertificateValidStatus.REVOKED, TODAY.minusYears(1), TODAY))
                .isEqualTo(CertificateUsability.REVOKED);
    }

    @Test
    @DisplayName("状态被标记为 EXPIRED 时即使日期未到也按过期处理")
    void markedExpiredWins() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.APPROVED,
                CertificateValidStatus.EXPIRED, LocalDate.of(2030, 1, 1), TODAY))
                .isEqualTo(CertificateUsability.EXPIRED);
    }

    @Test
    @DisplayName("驳回 -> 不可用")
    void rejected() {
        assertThat(CertificateValidity.evaluate(CertificateAuditStatus.REJECTED,
                CertificateValidStatus.VALID, LocalDate.of(2027, 1, 1), TODAY))
                .isEqualTo(CertificateUsability.REJECTED);
    }
}
