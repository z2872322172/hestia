package com.housekeeping.platform.workforce.domain;

import java.time.LocalDate;

/**
 * 证书可用性纯函数（docs/modules/05 第 7.2、10 节）。
 *
 * <p>判定只看四个事实：审核状态、有效性状态、到期日、今天。不查库、不取当前时间，
 * 因此可以针对"到期当天"这种边界写出确定的测试——用 {@code LocalDate.now()} 的写法
 * 在跨天时跑 CI 会出现"昨天还是绿的今天红了"。</p>
 *
 * <p>判定优先级：撤销 &gt; 过期 &gt; 驳回 &gt; 待审核。撤销排最前是因为它意味着
 * "这张证不可信"，即使日期还在有效期内也不能用；过期排第二是因为它最容易被误判成可用
 * （审核状态还是 APPROVED）。</p>
 */
public final class CertificateValidity {

    private CertificateValidity() {
    }

    /**
     * 判定单张证书是否可用于派单资格。
     *
     * @param auditStatus 审核状态
     * @param validStatus 有效性状态
     * @param expiresAt   到期日，{@code null} 表示长期有效
     * @param today       判定基准日
     * @return 可用性判定结果
     */
    public static CertificateUsability evaluate(CertificateAuditStatus auditStatus,
                                                CertificateValidStatus validStatus,
                                                LocalDate expiresAt,
                                                LocalDate today) {
        if (validStatus == CertificateValidStatus.REVOKED) {
            return CertificateUsability.REVOKED;
        }
        // 到期日当天仍然有效：证书写"有效期至 2026-10-23"意思是当天可用。
        // 用 isBefore 而不是 isAfter 就是为了这个"含当天"的语义。
        if (validStatus == CertificateValidStatus.EXPIRED
                || (expiresAt != null && expiresAt.isBefore(today))) {
            return CertificateUsability.EXPIRED;
        }
        if (auditStatus == CertificateAuditStatus.REJECTED) {
            return CertificateUsability.REJECTED;
        }
        if (auditStatus != CertificateAuditStatus.APPROVED) {
            return CertificateUsability.PENDING_REVIEW;
        }
        return CertificateUsability.USABLE;
    }
}
