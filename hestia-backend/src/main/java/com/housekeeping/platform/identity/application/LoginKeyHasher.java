package com.housekeeping.platform.identity.application;

import com.housekeeping.platform.identity.domain.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 登录标识哈希器。
 *
 * <p>为什么不是明文、也不是裸 SHA-256：</p>
 * <ul>
 *   <li>明文手机号进日志表 = 直接泄露个人信息；</li>
 *   <li>裸 SHA-256 对手机号无效：手机号空间只有约 10^11，跑完彩虹表是分钟级的事。</li>
 * </ul>
 *
 * <p>用服务端密钥做 HMAC 后，没有密钥就无法反查。密钥由 JWT 密钥派生
 * （{@code SHA-256("hestia:login-key:" + jwtSecret)}），做域名分离，
 * 避免同一把密钥直接用于两种用途；这样也不必再多要求一个环境变量。</p>
 */
@Component
public class LoginKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String DERIVE_PREFIX = "hestia:login-key:";

    private final byte[] key;

    public LoginKeyHasher(@Value("${app.security.jwt.secret:}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret 未配置，无法派生登录标识哈希密钥");
        }
        this.key = sha256(DERIVE_PREFIX + jwtSecret);
    }

    /**
     * 计算登录标识哈希。
     *
     * @param phone 归一化手机号
     * @return 64 位小写十六进制
     */
    public String hash(PhoneNumber phone) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(phone.fullNumber().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("登录标识哈希失败", exception);
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
