package com.housekeeping.platform.workforce.domain;

/**
 * 证书有效性状态（docs/modules/05 第 5.3 节）。
 *
 * <p>与审核状态分开存是必要的：一张证书可以"审核通过但已过期"，
 * 也可以"没过期但被撤销"（发现造假）。合成一个字段的话，
 * "过期后换新证"就得覆盖审核结论，审计记录会被抹掉。</p>
 */
public enum CertificateValidStatus {

    /** 有效。 */
    VALID,

    /** 已过期。 */
    EXPIRED,

    /** 已撤销。 */
    REVOKED;

    /**
     * 是否可能用于派单资格。
     *
     * <p>只是必要条件，还要看审核状态与到期日，见 {@link CertificateValidity}。</p>
     *
     * @return 状态为 {@code VALID} 返回 {@code true}
     */
    public boolean isPotentiallyUsable() {
        return this == VALID;
    }
}
