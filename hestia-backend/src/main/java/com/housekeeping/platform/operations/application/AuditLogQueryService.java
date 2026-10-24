package com.housekeeping.platform.operations.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.operations.persistence.entity.AuditLogEntity;
import com.housekeeping.platform.operations.persistence.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志查询服务（docs/modules/13 第 9.3 节）。
 *
 * <p>审计日志查询受权限控制（管理端专用接口），只读；
 * 审计内容按原样返回，写入侧已保证不含密码、Token 和支付凭证。</p>
 */
@Service
@RequiresPersistence
public class AuditLogQueryService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogQueryService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 审计日志视图。
     *
     * @param id                主键
     * @param traceId           链路标识
     * @param operatorAccountId 操作账号 ID
     * @param operatorRole      操作角色
     * @param actionType        操作类型
     * @param businessType      业务类型
     * @param businessId        业务 ID
     * @param beforeJson        操作前摘要
     * @param afterJson         操作后摘要
     * @param reason            操作原因
     * @param ipAddress         客户端 IP
     * @param createdAt         操作时间
     */
    public record AuditLogView(Long id, String traceId, Long operatorAccountId, String operatorRole,
                               String actionType, String businessType, String businessId,
                               String beforeJson, String afterJson, String reason,
                               String ipAddress, LocalDateTime createdAt) {
    }

    /**
     * 分页查询审计日志。
     *
     * @param actionType        操作类型过滤（可空）
     * @param businessType      业务类型过滤（可空）
     * @param operatorAccountId 操作账号过滤（可空）
     * @param from              操作日期起（含，可空）
     * @param to                操作日期止（含，可空）
     * @param page              页码
     * @param size              条数
     * @return 分页审计日志
     */
    @Transactional(readOnly = true)
    public PageResult<AuditLogView> page(String actionType, String businessType,
                                         Long operatorAccountId, LocalDate from, LocalDate to,
                                         int page, int size) {
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(actionType != null && !actionType.isBlank(),
                        AuditLogEntity::getActionType, actionType)
                .eq(businessType != null && !businessType.isBlank(),
                        AuditLogEntity::getBusinessType, businessType)
                .eq(operatorAccountId != null, AuditLogEntity::getOperatorAccountId,
                        operatorAccountId)
                .ge(from != null, AuditLogEntity::getCreatedAt,
                        from == null ? null : from.atStartOfDay())
                .lt(to != null, AuditLogEntity::getCreatedAt,
                        to == null ? null : to.plusDays(1).atStartOfDay())
                .orderByDesc(AuditLogEntity::getId);
        Page<AuditLogEntity> result = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<AuditLogView> records = result.getRecords().stream()
                .map(AuditLogQueryService::toView)
                .toList();
        return new PageResult<>(records, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal());
    }

    /**
     * 审计日志详情。
     *
     * @param id 主键
     * @return 审计日志视图
     */
    @Transactional(readOnly = true)
    public AuditLogView detail(long id) {
        AuditLogEntity entity = auditLogMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.AUDIT_LOG_NOT_FOUND);
        }
        return toView(entity);
    }

    private static AuditLogView toView(AuditLogEntity entity) {
        return new AuditLogView(entity.getId(), entity.getTraceId(), entity.getOperatorAccountId(),
                entity.getOperatorRole(), entity.getActionType(), entity.getBusinessType(),
                entity.getBusinessId(), entity.getBeforeJson(), entity.getAfterJson(),
                entity.getReason(), entity.getIpAddress(), entity.getCreatedAt());
    }
}
