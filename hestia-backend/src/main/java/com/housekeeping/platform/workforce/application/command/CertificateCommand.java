package com.housekeeping.platform.workforce.application.command;

import java.time.LocalDate;

/**
 * 提交证书命令（服务人员端）。
 *
 * <p>{@code certificateNo} 允许为空：有些材料只需上传照片（如背景核验回执），
 * 编号由核验机构保管。有编号时会被加密存储并计算摘要用于查重。</p>
 *
 * @param certificateType 证书类型
 * @param certificateNo   证书编号，可空
 * @param fileId          附件文件 ID，可空（先登记后补传）
 * @param issuedAt        发证日，可空
 * @param expiresAt       到期日，{@code null} 表示长期有效
 * @param remark          备注
 */
public record CertificateCommand(String certificateType,
                                 String certificateNo,
                                 Long fileId,
                                 LocalDate issuedAt,
                                 LocalDate expiresAt,
                                 String remark) {
}
