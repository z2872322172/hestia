package com.housekeeping.platform.operations.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.operations.domain.ConfigValues;
import com.housekeeping.platform.operations.persistence.entity.SystemConfigEntity;
import com.housekeeping.platform.operations.persistence.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统配置应用服务（docs/modules/13 第 10 节）。
 *
 * <p>修改配置的完整链路：expectedVersion 乐观锁条件更新 → 版本冲突返回
 * SYSTEM_CONFIG_VERSION_CONFLICT → 成功后同事务写审计（前后值摘要）。
 * 业务模块读取配置必须走本服务（integerValue 等），不直接查表。</p>
 */
@Service
@RequiresPersistence
public class SystemConfigService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SystemConfigMapper configMapper;

    private final AuditRecorder auditRecorder;

    private final CurrentAccountResolver currentAccountResolver;

    public SystemConfigService(SystemConfigMapper configMapper,
                               AuditRecorder auditRecorder,
                               CurrentAccountResolver currentAccountResolver) {
        this.configMapper = configMapper;
        this.auditRecorder = auditRecorder;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 配置视图。
     *
     * @param configKey   配置键
     * @param configName  配置名称
     * @param configGroup 配置分组
     * @param valueType   值类型
     * @param value       配置值（展示文本）
     * @param description 说明
     * @param status      状态
     * @param version     版本号
     * @param updatedAt   更新时间
     */
    public record SystemConfigView(String configKey, String configName, String configGroup,
                                   String valueType, String value, String description,
                                   String status, long version, LocalDateTime updatedAt) {
    }

    /**
     * 查询全部配置（按分组 + 键排序）。
     *
     * @return 配置列表
     */
    @Transactional(readOnly = true)
    public List<SystemConfigView> listAll() {
        return configMapper.selectList(new LambdaQueryWrapper<SystemConfigEntity>()
                        .orderByAsc(SystemConfigEntity::getConfigGroup)
                        .orderByAsc(SystemConfigEntity::getConfigKey))
                .stream()
                .map(SystemConfigService::toView)
                .toList();
    }

    /**
     * 查询单个配置。
     *
     * @param configKey 配置键
     * @return 配置视图
     */
    @Transactional(readOnly = true)
    public SystemConfigView get(String configKey) {
        return toView(requireEntity(configKey));
    }

    /**
     * 修改配置值（管理端）。
     *
     * @param configKey       配置键
     * @param rawValue        新配置值（原始文本，按类型校验后 JSON 编码）
     * @param reason          修改原因（写入审计）
     * @param expectedVersion 客户端声明的当前版本
     * @return 修改后的配置视图
     */
    @Transactional
    public SystemConfigView update(String configKey, String rawValue, String reason,
                                   Long expectedVersion) {
        SystemConfigEntity entity = requireEntity(configKey);
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "配置已停用，不允许修改");
        }
        if (expectedVersion == null || entity.getVersion() == null
                || expectedVersion != entity.getVersion()) {
            throw new BusinessException(ErrorCode.SYSTEM_CONFIG_VERSION_CONFLICT);
        }
        String newValueJson = ConfigValues.encode(entity.getValueType(), rawValue);
        String beforeJson = auditJson(entity.getValueJson());
        int updated = configMapper.updateValue(configKey, newValueJson,
                currentAccountResolver.requireAccountId(), expectedVersion);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_CONFIG_VERSION_CONFLICT);
        }
        auditRecorder.record("CONFIG_UPDATE", "SYSTEM_CONFIG", configKey,
                beforeJson, auditJson(newValueJson), reason);
        return toView(requireEntity(configKey));
    }

    /**
     * 读取 INTEGER 配置（业务模块取值入口）。
     *
     * <p>配置缺失、停用或类型异常时返回默认值：配置台账缺失不应阻断业务主流程，
     * 运行时取值仍以 application.yml 为准。</p>
     *
     * @param configKey    配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    @Transactional(readOnly = true)
    public long integerValue(String configKey, long defaultValue) {
        SystemConfigEntity entity = configMapper.selectByKey(configKey);
        if (entity == null || !STATUS_ACTIVE.equals(entity.getStatus())) {
            return defaultValue;
        }
        try {
            return ConfigValues.asInteger(entity.getValueJson());
        } catch (BusinessException exception) {
            return defaultValue;
        }
    }

    private SystemConfigEntity requireEntity(String configKey) {
        SystemConfigEntity entity = configMapper.selectByKey(configKey);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND);
        }
        return entity;
    }

    /** 审计摘要：{"value": <原始 JSON 标量>}，配置值本身是非敏感参数。 */
    private static String auditJson(String valueJson) {
        return "{\"value\":" + valueJson + "}";
    }

    private static SystemConfigView toView(SystemConfigEntity entity) {
        String value = "STRING".equals(entity.getValueType())
                ? ConfigValues.asString(entity.getValueJson())
                : entity.getValueJson();
        return new SystemConfigView(entity.getConfigKey(), entity.getConfigName(),
                entity.getConfigGroup(), entity.getValueType(), value,
                entity.getDescription(), entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(), entity.getUpdatedAt());
    }
}
