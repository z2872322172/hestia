package com.housekeeping.platform.operations.application;

import com.housekeeping.platform.common.api.RequestContext;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountProvider;
import com.housekeeping.platform.operations.persistence.entity.AuditLogEntity;
import com.housekeeping.platform.operations.persistence.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计记录器（docs/modules/13 第 9 节）。
 *
 * <p><b>只追加</b>：只提供 record 与查询能力，审计写入参与调用方的业务事务
 * （传播级别 REQUIRED），保证"高风险操作的审计写入与业务事务保持一致"。
 * 操作账号 / 角色 / traceId / IP / User-Agent 自动从当前上下文提取；
 * 定时任务等系统操作账号为空。审计内容不保存密码、Token 和支付凭证，
 * 调用方传入的摘要必须是脱敏后的业务字段。</p>
 */
@Component
@RequiresPersistence
public class AuditRecorder {

    private static final int REASON_MAX = 1000;

    private static final int USER_AGENT_MAX = 512;

    private final AuditLogMapper auditLogMapper;

    private final CurrentAccountProvider currentAccountProvider;

    public AuditRecorder(AuditLogMapper auditLogMapper, CurrentAccountProvider currentAccountProvider) {
        this.auditLogMapper = auditLogMapper;
        this.currentAccountProvider = currentAccountProvider;
    }

    /**
     * 追加一条审计日志（参与调用方事务）。
     *
     * @param actionType   操作类型，如 CONFIG_UPDATE
     * @param businessType 业务类型，如 SYSTEM_CONFIG
     * @param businessId   业务 ID（订单号 / 工单号 / 配置键等，可空）
     * @param beforeJson   操作前摘要（脱敏后）
     * @param afterJson    操作后摘要（脱敏后）
     * @param reason       操作原因（可空）
     */
    @Transactional
    public void record(String actionType, String businessType, String businessId,
                       String beforeJson, String afterJson, String reason) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setTraceId(RequestContext.getTraceId());
        auditLog.setOperatorAccountId(currentAccountProvider.currentAccountId());
        auditLog.setOperatorRole(currentAccountProvider.currentRoleCode());
        auditLog.setActionType(actionType);
        auditLog.setBusinessType(businessType);
        auditLog.setBusinessId(businessId);
        auditLog.setBeforeJson(beforeJson);
        auditLog.setAfterJson(afterJson);
        auditLog.setReason(truncate(reason, REASON_MAX));

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            auditLog.setIpAddress(request.getRemoteAddr());
            auditLog.setUserAgent(truncate(request.getHeader("User-Agent"), USER_AGENT_MAX));
        }
        auditLogMapper.insert(auditLog);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
