package com.housekeeping.platform.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 手机号归一化测试。
 *
 * <p>重点不是"能不能解析"，而是"同一个号码是否只会产生一种存储形态"——
 * 唯一索引建在 {@code (phone_country_code, phone)} 上，归一化不确定就会注册出重复账号。</p>
 */
class PhoneNumberTest {

    @Test
    @DisplayName("带空格、横线、括号的号码归一化为纯数字")
    void normalizesPunctuation() {
        PhoneNumber phone = PhoneNumber.parse("+86", "138-0013-8000");
        assertThat(phone.countryCode()).isEqualTo("+86");
        assertThat(phone.number()).isEqualTo("13800138000");

        assertThat(PhoneNumber.parse(null, "138 0013 8000").number()).isEqualTo("13800138000");
        assertThat(PhoneNumber.parse("86", "(138)00138000").countryCode()).isEqualTo("+86");
    }

    @Test
    @DisplayName("号码里重复写了 86 前缀时自动剥离")
    void stripsRedundantCountryCode() {
        assertThat(PhoneNumber.parse("+86", "+8613800138000").number()).isEqualTo("13800138000");
        assertThat(PhoneNumber.parse("+86", "8613800138000").number()).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("非法号码被拒绝")
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> PhoneNumber.parse("+86", "12345"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumber.parse("+86", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumber.parse("+86", "23800138000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("脱敏只保留前三位与后四位")
    void masksNumber() {
        assertThat(PhoneNumber.parse("+86", "13800138000").masked()).isEqualTo("138****8000");
    }

    @Test
    @DisplayName("非法区号被拒绝")
    void rejectsInvalidCountryCode() {
        assertThatThrownBy(() -> PhoneNumber.parse("abc", "13800138000"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
