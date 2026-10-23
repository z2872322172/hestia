package com.housekeeping.platform.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 敏感字段加密与摘要（AES-256-GCM + HMAC-SHA256）。
 *
 * <p>用于证书编号这类"既需要回显给本人核对、又不能明文落库"的字段：</p>
 * <ul>
 *   <li>{@link #encrypt} 产出可直接存 {@code VARBINARY} 的密文，供授权查看时解密；</li>
 *   <li>{@link #hmac} 产出稳定的十六进制摘要，供唯一索引做"同一张证不能重复登记"校验。</li>
 * </ul>
 *
 * <p>为什么不用同一个密钥做两件事：加密密钥泄漏意味着能解出原文，
 * 摘要密钥泄漏只意味着能对候选值做比对。两者分开派生（加不同标签再做 SHA-256），
 * 是为了不把两种风险绑在一起。</p>
 *
 * <p>GCM 每次加密都用新的随机 IV，所以同一个证书编号两次加密得到的密文不同——
 * 这正是"密文不可用于相等比较"的原因，查重必须走 {@link #hmac}。</p>
 */
@Component
public class FieldCipher {

    private static final Logger log = LoggerFactory.getLogger(FieldCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** GCM 推荐 96 bit IV。 */
    private static final int IV_LENGTH = 12;

    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    private final SecretKeySpec encryptionKey;

    private final SecretKeySpec hmacKey;

    /**
     * 构造器。
     *
     * @param rawKey 配置中的密钥原文，长度不限；实际密钥由 SHA-256 派生
     */
    public FieldCipher(@Value("${app.security.field-key:}") String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            // 不直接抛异常：单测与不需要加密字段的 profile 也要能加载上下文。
            // 真正用到加密时会明确报错，而不是悄悄存明文。
            this.encryptionKey = null;
            this.hmacKey = null;
            log.warn("未配置 app.security.field-key，敏感字段加密不可用（证书编号提交将被拒绝）");
            return;
        }
        this.encryptionKey = new SecretKeySpec(derive(rawKey, "enc"), "AES");
        this.hmacKey = new SecretKeySpec(derive(rawKey, "hmac"), HMAC_ALGORITHM);
    }

    /**
     * 加密能力是否可用。
     *
     * @return 已配置密钥返回 {@code true}
     */
    public boolean isConfigured() {
        return encryptionKey != null;
    }

    /**
     * 加密。
     *
     * @param plain 明文
     * @return {@code IV || 密文 || 认证标签}
     * @throws IllegalStateException 未配置密钥
     */
    public byte[] encrypt(String plain) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("字段加密失败", exception);
        }
    }

    /**
     * 解密。
     *
     * @param payload {@link #encrypt} 的产物
     * @return 明文
     * @throws IllegalStateException 未配置密钥或密文被篡改
     */
    public String decrypt(byte[] payload) {
        requireConfigured();
        if (payload == null || payload.length <= IV_LENGTH) {
            throw new IllegalStateException("密文长度非法");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            // GCM 校验失败就是"密文被改过或密钥不对"，两者都不该继续
            throw new IllegalStateException("字段解密失败", exception);
        }
    }

    /**
     * 计算 HMAC-SHA256 摘要（十六进制，64 字符）。
     *
     * @param value 原文
     * @return 摘要
     * @throws IllegalStateException 未配置密钥
     */
    public String hmac(String value) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("摘要计算失败", exception);
        }
    }

    private void requireConfigured() {
        if (encryptionKey == null) {
            throw new IllegalStateException("未配置 app.security.field-key，无法处理敏感字段");
        }
    }

    /**
     * 从配置密钥派生用途独立的子密钥。
     *
     * <p>加标签再哈希，保证 AES 密钥与 HMAC 密钥不同：同一份配置不会因为
     * "加密和查重共用一把钥匙"而让任一处的泄漏影响另一处。</p>
     */
    private static byte[] derive(String rawKey, String purpose) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((rawKey + "|" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
