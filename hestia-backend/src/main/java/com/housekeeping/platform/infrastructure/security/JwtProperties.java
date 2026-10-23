package com.housekeeping.platform.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。
 *
 * <p>密钥只从环境变量读取（{@code JWT_SECRET}），代码与配置文件里不得出现生产密钥。
 * HS256 要求密钥至少 32 字节（256 bit），长度不足时 {@link JwtTokenService} 直接启动失败，
 * 不允许"悄悄降级成弱密钥"。</p>
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** HMAC 密钥，至少 32 字节；为空或过短时应用启动失败。 */
    private String secret;

    /** 访问令牌有效期，docs/modules/01 建议 60 至 120 分钟。 */
    private Duration accessTokenTtl = Duration.ofMinutes(90);

    /** 签发者标识，写入 iss 声明，便于多环境区分。 */
    private String issuer = "hestia-platform";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
