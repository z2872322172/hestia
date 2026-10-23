package com.housekeeping.platform.workforce.api.dto;

import com.housekeeping.platform.workforce.application.command.CertificateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 提交证书请求（服务人员端）。
 *
 * @param certificateType 证书类型
 * @param certificateNo   证书编号，可空
 * @param fileId          附件文件 ID，可空
 * @param issuedAt        发证日
 * @param expiresAt       到期日，空表示长期有效
 * @param remark          备注
 */
public record CertificateSubmitRequest(
        @NotBlank(message = "证书类型不能为空") String certificateType,
        @Size(max = 128, message = "证书编号过长") String certificateNo,
        Long fileId,
        LocalDate issuedAt,
        LocalDate expiresAt,
        @Size(max = 500, message = "备注过长") String remark) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public CertificateCommand toCommand() {
        return new CertificateCommand(certificateType, certificateNo, fileId, issuedAt, expiresAt, remark);
    }
}
