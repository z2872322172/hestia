package com.housekeeping.platform.workforce.domain;

/**
 * 证书可用性判定结果。
 *
 * <p>比 {@code boolean usable} 多带一层原因：运营看到"健康证不可用"时，
 * 需要立刻知道是"还没审"还是"过期了"——前者等审核，后者催换证，
 * 处理动作完全不同。</p>
 */
public enum CertificateUsability {

    /** 可用于派单资格。 */
    USABLE,

    /** 尚未审核。 */
    PENDING_REVIEW,

    /** 审核驳回。 */
    REJECTED,

    /** 已撤销。 */
    REVOKED,

    /** 已过期。 */
    EXPIRED;

    /**
     * 是否可用。
     *
     * @return 可用返回 {@code true}
     */
    public boolean usable() {
        return this == USABLE;
    }
}
