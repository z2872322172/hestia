package com.housekeeping.platform.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密码策略测试。
 */
class PasswordPolicyTest {

    private final PhoneNumber phone = PhoneNumber.parse("+86", "13800138000");

    @Test
    @DisplayName("合法密码通过")
    void acceptsValidPassword() {
        assertThatCode(() -> PasswordPolicy.validate("Hestia2026", phone)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("过短或过长被拒绝")
    void rejectsLengthOutOfRange() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a1b2c3", phone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度");
        assertThatThrownBy(() -> PasswordPolicy.validate("a1" + "x".repeat(64), phone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度");
    }

    @Test
    @DisplayName("纯字母或纯数字被拒绝")
    void requiresBothLetterAndDigit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefgh", phone))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678", phone))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("密码包含手机号被拒绝")
    void rejectsPasswordContainingPhone() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a13800138000", phone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手机号");
    }
}
