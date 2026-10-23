package com.housekeeping.platform.infrastructure.security;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与解析测试。
 *
 * <p>不启动 Spring：签发与验签是纯密码学行为，脱离容器测试更快也更稳定。</p>
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";

    private static JwtTokenService service(Duration ttl, String issuer) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenTtl(ttl);
        properties.setIssuer(issuer);
        return new JwtTokenService(properties);
    }

    @Test
    @DisplayName("签发后可解析出同样的账号、角色与令牌版本")
    void issuesAndParsesToken() {
        JwtTokenService service = service(Duration.ofMinutes(90), "hestia-test");
        IssuedToken issued = service.issue(1001L, "CUSTOMER", 3L);

        TokenPayload payload = service.parse(issued.token());

        assertThat(payload.accountId()).isEqualTo(1001L);
        assertThat(payload.roleCode()).isEqualTo("CUSTOMER");
        assertThat(payload.tokenVersion()).isEqualTo(3L);
        assertThat(payload.tokenId()).isEqualTo(issued.tokenId());
        assertThat(issued.expiresInSeconds()).isEqualTo(Duration.ofMinutes(90).toSeconds());
    }

    @Test
    @DisplayName("过期令牌抛出 AUTH_TOKEN_EXPIRED，与无效令牌区分")
    void expiredTokenThrowsExpired() {
        JwtTokenService service = service(Duration.ofSeconds(-60), "hestia-test");
        String token = service.issue(1001L, "CUSTOMER", 0L).token();

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("被篡改的令牌抛出 AUTH_INVALID_TOKEN")
    void tamperedTokenThrowsInvalid() {
        JwtTokenService service = service(Duration.ofMinutes(30), "hestia-test");
        String token = service.issue(1001L, "CUSTOMER", 0L).token();
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    @DisplayName("签发者不匹配的令牌被拒绝")
    void rejectsTokenFromAnotherIssuer() {
        JwtTokenService issuerA = service(Duration.ofMinutes(30), "issuer-a");
        JwtTokenService issuerB = service(Duration.ofMinutes(30), "issuer-b");
        String token = issuerA.issue(1001L, "CUSTOMER", 0L).token();

        assertThatThrownBy(() -> issuerB.parse(token))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("密钥不足 32 字节时拒绝启动，不静默降级")
    void rejectsWeakSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");

        assertThatThrownBy(() -> new JwtTokenService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
