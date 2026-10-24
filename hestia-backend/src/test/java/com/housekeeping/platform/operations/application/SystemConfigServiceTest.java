package com.housekeeping.platform.operations.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.operations.persistence.entity.SystemConfigEntity;
import com.housekeeping.platform.operations.persistence.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统配置服务测试（docs/modules/13 第 20.1 节"配置类型和版本"）。
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    private static final String CONFIG_KEY = "payment_timeout_minutes";

    @Mock
    private SystemConfigMapper configMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private CurrentAccountResolver currentAccountResolver;

    private SystemConfigService service;

    @BeforeEach
    void setUp() {
        service = new SystemConfigService(configMapper, auditRecorder, currentAccountResolver);
    }

    @Test
    @DisplayName("修改成功：条件更新 + 同事务写审计")
    void updatesValueAndWritesAudit() {
        // 第一次读取取当前值，更新成功后的重读返回新值新版本
        when(configMapper.selectByKey(CONFIG_KEY))
                .thenReturn(activeConfig("15", 3L), updatedConfig("16", 4L));
        when(currentAccountResolver.requireAccountId()).thenReturn(7L);
        when(configMapper.updateValue(eq(CONFIG_KEY), eq("16"), eq(7L), eq(3L))).thenReturn(1);

        SystemConfigService.SystemConfigView view =
                service.update(CONFIG_KEY, "16", "缩短支付窗口", 3L);

        assertThat(view.value()).isEqualTo("16");
        assertThat(view.version()).isEqualTo(4L);
        verify(auditRecorder).record(eq("CONFIG_UPDATE"), eq("SYSTEM_CONFIG"), eq(CONFIG_KEY),
                contains("\"value\":15"), contains("\"value\":16"), eq("缩短支付窗口"));
    }

    @Test
    @DisplayName("版本冲突：条件更新影响 0 行时不写审计")
    void rejectsWhenConditionalUpdateMisses() {
        when(configMapper.selectByKey(CONFIG_KEY)).thenReturn(activeConfig("15", 3L));
        when(currentAccountResolver.requireAccountId()).thenReturn(7L);
        when(configMapper.updateValue(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.update(CONFIG_KEY, "16", null, 3L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SYSTEM_CONFIG_VERSION_CONFLICT));
        verify(auditRecorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("expectedVersion 与当前版本不一致：直接拒绝，不触库")
    void rejectsStaleExpectedVersion() {
        when(configMapper.selectByKey(CONFIG_KEY)).thenReturn(activeConfig("15", 4L));

        assertThatThrownBy(() -> service.update(CONFIG_KEY, "16", null, 3L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SYSTEM_CONFIG_VERSION_CONFLICT));
        verify(configMapper, never()).updateValue(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("值类型不合法：拒绝且不写审计")
    void rejectsInvalidValueForType() {
        when(configMapper.selectByKey(CONFIG_KEY)).thenReturn(activeConfig("15", 3L));

        assertThatThrownBy(() -> service.update(CONFIG_KEY, "abc", null, 3L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SYSTEM_CONFIG_TYPE_INVALID));
        verify(auditRecorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("停用配置不允许修改")
    void rejectsDisabledConfig() {
        SystemConfigEntity entity = activeConfig("15", 3L);
        entity.setStatus("DISABLED");
        when(configMapper.selectByKey(CONFIG_KEY)).thenReturn(entity);

        assertThatThrownBy(() -> service.update(CONFIG_KEY, "16", null, 3L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    @DisplayName("integerValue：缺失回默认值，存在取配置值")
    void readsIntegerWithFallback() {
        assertThat(service.integerValue(CONFIG_KEY, 30L)).isEqualTo(30L);
        when(configMapper.selectByKey(CONFIG_KEY)).thenReturn(activeConfig("15", 3L));
        assertThat(service.integerValue(CONFIG_KEY, 30L)).isEqualTo(15L);
    }

    @Test
    @DisplayName("配置不存在：SYSTEM_CONFIG_NOT_FOUND")
    void rejectsMissingConfig() {
        when(configMapper.selectByKey(CONFIG_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.get(CONFIG_KEY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));
    }

    @Test
    @DisplayName("listAll：按实体映射视图")
    void mapsListToViews() {
        when(configMapper.selectList(any())).thenReturn(List.of(activeConfig("15", 3L)));

        List<SystemConfigService.SystemConfigView> views = service.listAll();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).configKey()).isEqualTo(CONFIG_KEY);
        assertThat(views.get(0).value()).isEqualTo("15");
    }

    private static SystemConfigEntity activeConfig(String value, long version) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setId(1L);
        entity.setConfigKey(CONFIG_KEY);
        entity.setConfigName("支付超时分钟数");
        entity.setConfigGroup("BOOKING");
        entity.setValueType("INTEGER");
        entity.setValueJson(value);
        entity.setStatus("ACTIVE");
        entity.setVersion(version);
        return entity;
    }

    private static SystemConfigEntity updatedConfig(String value, long version) {
        return activeConfig(value, version);
    }
}
