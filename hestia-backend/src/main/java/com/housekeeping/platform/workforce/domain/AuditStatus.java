package com.housekeeping.platform.workforce.domain;

/**
 * 人员档案审核状态（docs/modules/05 第 5.2 节）。
 *
 * <p>审核与工作状态是两条独立的线：{@code audit_status} 由运营对"这个人资料是否可信"下结论，
 * {@code work_status} 表达"这个人现在是否在岗"。只有审核通过的人才能进入 {@code ACTIVE}，
 * 但审核通过不等于自动在岗——入职时间由运营安排。</p>
 */
public enum AuditStatus {

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
