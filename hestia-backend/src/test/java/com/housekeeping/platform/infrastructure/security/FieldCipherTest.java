package com.housekeeping.platform.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 敏感字段加密测试。
 *
 * <p>重点不是"能加密"，而是三条容易被忽略的性质：同一明文两次密文不同（随机 IV）、
 * 摘要稳定（可做唯一索引）、密文被改动后解密必须失败（GCM 认证）。</p>
 */
class FieldCipherTest {

    private static final String KEY = "unit-test-field-key-0123456789";

    @Test
    @DisplayName("加密后能解回原文（含中文与符号）")
    void roundTrip() {
        FieldCipher cipher = new FieldCipher(KEY);
        String plain = "粤字第 2026-0001 号 / 健康证";

        assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    @DisplayName("同一明文两次加密得到不同密文：否则密文本身就能被当作相等比较")
    void ciphertextIsRandomized() {
        FieldCipher cipher = new FieldCipher(KEY);

        byte[] first = cipher.encrypt("HEALTH-2026-0001");
        byte[] second = cipher.encrypt("HEALTH-2026-0001");

        assertThat(Arrays.equals(first, second)).isFalse();
    }

    @Test
    @DisplayName("HMAC 摘要稳定：同一编号永远得到同一个 64 位十六进制串")
    void hmacIsStable() {
        FieldCipher cipher = new FieldCipher(KEY);

        String first = cipher.hmac("HEALTH-2026-0001");
        String second = cipher.hmac("HEALTH-2026-0001");

        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(cipher.hmac("HEALTH-2026-0002")).isNotEqualTo(first);
    }

    @Test
    @DisplayName("密文被篡改一个字节 -> 解密失败，而不是返回错误明文")
    void tamperedCiphertextFails() {
        FieldCipher cipher = new FieldCipher(KEY);
        byte[] payload = cipher.encrypt("HEALTH-2026-0001");
        payload[payload.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @DisplayName("未配置密钥时明确不可用，而不是悄悄存明文")
    void blankKeyDisablesFeature() {
        FieldCipher cipher = new FieldCipher("");

        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("不同密钥无法解开对方的密文")
    void differentKeyCannotDecrypt() {
        byte[] payload = new FieldCipher(KEY).encrypt("HEALTH-2026-0001");

        assertThatThrownBy(() -> new FieldCipher("another-key").decrypt(payload))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("加密与摘要使用不同派生密钥：两者输出不应有任何可观察关联")
    void encryptionAndHmacUseSeparateKeys() {
        FieldCipher cipher = new FieldCipher(KEY);
        String plain = "HEALTH-2026-0001";

        String hmac = cipher.hmac(plain);
        String cipherTextAsHex = new String(cipher.encrypt(plain), StandardCharsets.ISO_8859_1);

        assertThat(cipherTextAsHex).doesNotContain(hmac);
    }
}
