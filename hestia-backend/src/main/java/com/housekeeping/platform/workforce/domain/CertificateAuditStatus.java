package com.housekeeping.platform.workforce.domain;

/**
 * 证书审核状态（docs/modules/05 第 5.3 节）。
 *
 * <p>与人员档案的 {@link AuditStatus} 是同一套取值，但<b>刻意分成两个类型</b>：
 * 两者会各自演进（证书可能增加"需复检"），而且方法签名里都叫 {@code AuditStatus} 时，
 * 把人员审核结果写进证书表这种错误编译器拦不住。</p>
 */
public enum CertificateAuditStatus {

    /** 待审核。 */
    PENDING,

    /** 审核通过。 */
    APPROVED,

    /** 审核驳回。 */
    REJECTED;

    /**
     * 是否审核通过。
     *
     * @return 通过返回 {@code true}
     */
    public boolean isApproved() {
        return this == APPROVED;
    }
}
