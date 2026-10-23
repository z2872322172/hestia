package com.housekeeping.platform.workforce.api.dto;

import com.housekeeping.platform.workforce.application.command.CertificateReviewCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 证书审核请求（运营端）。
 *
 * @param auditStatus 审核结论：APPROVED / REJECTED
 * @param validStatus 有效性：VALID / EXPIRED / REVOKED，可空表示不变
 * @param remark      审核备注
 */
public record CertificateReviewRequest(
        @NotBlank(message = "审核结论不能为空") String auditStatus,
        String validStatus,
        @Size(max = 500, message = "备注过长") String remark) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public CertificateReviewCommand toCommand() {
        return new CertificateReviewCommand(auditStatus, validStatus, remark);
    }
}
