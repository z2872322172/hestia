package com.housekeeping.platform.workforce.application.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证书视图。
 *
 * <p><b>刻意不含</b>证书编号（密文或明文）与附件 URL：证书编号是敏感个人信息，
 * 附件只能通过受控文件接口换取临时地址。视图里放进去，迟早会被日志或前端缓存带出去。</p>
 *
 * <p>{@code usable} 由 {@code CertificateValidity} 现算，不落库：
 * 存一个"是否可用"的字段就得靠定时任务维护，任务漏跑一次就会把过期证当成有效。</p>
 *
 * @param certificateId   证书 ID
 * @param certificateType 证书类型
 * @param issuedAt        发证日
 * @param expiresAt       到期日，{@code null} 表示长期有效
 * @param auditStatus     审核状态
 * @param validStatus     有效性状态
 * @param usable          当前是否可用于派单
 * @param usability       可用性判定（USABLE / PENDING_REVIEW / EXPIRED / REVOKED / REJECTED）
 * @param verifiedAt      审核时间
 * @param remark          备注
 */
public record CertificateView(long certificateId,
                              String certificateType,
                              LocalDate issuedAt,
                              LocalDate expiresAt,
                              String auditStatus,
                              String validStatus,
                              boolean usable,
                              String usability,
                              LocalDateTime verifiedAt,
                              String remark) {
}
