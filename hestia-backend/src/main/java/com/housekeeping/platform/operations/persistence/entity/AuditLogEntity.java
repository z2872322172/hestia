package com.housekeeping.platform.operations.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感操作审计日志，对应表 {@code audit_log}（V1 技术底座已建表，docs/modules/13 第 9 节）。
 *
 * <p><b>只追加，不更新、不删除</b>：实体不带 version 与 updated_at，
 * 也不提供任何修改方法；查询受管理端权限控制。审计内容不保存密码、Token 和支付凭证。</p>
 */
@Data
@TableName("audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链路标识，便于与请求日志对齐。 */
    private String traceId;

    /** 操作账号 ID，系统操作为空。 */
    private Long operatorAccountId;

    /** 操作时的主要角色快照。 */
    private String operatorRole;

    /** 操作类型，如 CONFIG_UPDATE。 */
    private String actionType;

    /** 业务类型，如 SYSTEM_CONFIG。 */
    private String businessType;

    /** 业务 ID，字符串以兼容订单号、工单号和人员编号。 */
    private String businessId;

    /** 操作前摘要。 */
    private String beforeJson;

    /** 操作后摘要。 */
    private String afterJson;

    /** 操作原因。 */
    private String reason;

    /** 客户端 IP（兼容 IPv6）。 */
    private String ipAddress;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 操作时间。 */
    private LocalDateTime createdAt;
}
