package com.housekeeping.platform.workforce.application.command;

/**
 * 证书审核命令（运营端）。
 *
 * @param auditStatus 审核结论：{@code APPROVED} / {@code REJECTED}
 * @param validStatus 有效性：{@code VALID} / {@code EXPIRED} / {@code REVOKED}，可空表示不变
 * @param remark      审核备注（驳回原因必填才有意义，但首版不强制）
 */
public record CertificateReviewCommand(String auditStatus, String validStatus, String remark) {
}
