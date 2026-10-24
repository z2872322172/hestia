package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 系统配置值解析测试（docs/modules/13 第 20.1 节"配置类型和版本"）。
 */
class ConfigValuesTest {

    @Test
    @DisplayName("INTEGER：裸标量与带引号标量均可解析")
    void parsesInteger() {
        assertThat(ConfigValues.asInteger("15")).isEqualTo(15L);
        assertThat(ConfigValues.asInteger("\"15\"")).isEqualTo(15L);
        assertThat(ConfigValues.asInteger("-3")).isEqualTo(-3L);
    }

    @Test
    @DisplayName("INTEGER：非数字拒绝")
    void rejectsInvalidInteger() {
        assertThatThrownBy(() -> ConfigValues.asInteger("abc"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SYSTEM_CONFIG_TYPE_INVALID));
    }

    @Test
    @DisplayName("DECIMAL / BOOLEAN 解析")
    void parsesDecimalAndBoolean() {
        assertThat(ConfigValues.asDecimal("0.3")).isEqualByComparingTo("0.3");
        assertThat(ConfigValues.asBoolean("true")).isTrue();
        assertThat(ConfigValues.asBoolean("\"false\"")).isFalse();
    }

    @Test
    @DisplayName("STRING：去 JSON 引号并还原转义")
    void parsesString() {
        assertThat(ConfigValues.asString("\"polite text\"")).isEqualTo("polite text");
        assertThat(ConfigValues.asString("\"a\\\"b\"")).isEqualTo("a\"b");
    }

    @Test
    @DisplayName("encode：合法值按类型编码")
    void encodesValidValues() {
        assertThat(ConfigValues.encode("INTEGER", " 16 ")).isEqualTo("16");
        assertThat(ConfigValues.encode("DECIMAL", "0.25")).isEqualTo("0.25");
        assertThat(ConfigValues.encode("BOOLEAN", "TRUE")).isEqualTo("true");
        assertThat(ConfigValues.encode("STRING", "a\"b"))
                .isEqualTo("\"a\\\"b\"");
        assertThat(ConfigValues.encode("JSON", "{\"k\":1}")).isEqualTo("{\"k\":1}");
    }

    @Test
    @DisplayName("encode：类型不匹配或空值拒绝")
    void rejectsInvalidEncode() {
        assertThatThrownBy(() -> ConfigValues.encode("INTEGER", "abc"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ConfigValues.encode("BOOLEAN", "yes"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ConfigValues.encode("STRING", " "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ConfigValues.encode("SECRET", "x"))
                .isInstanceOf(BusinessException.class);
    }
}
