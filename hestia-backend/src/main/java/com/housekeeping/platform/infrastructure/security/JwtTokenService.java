package com.housekeeping.platform.infrastructure.security;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.id.IdGenerator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>只做密码学层面的事：签发、验签、取声明、判过期。
 * "这个账号还有效吗、这个角色还被授予吗"属于账号状态判断，由 identity 模块负责——
 * 令牌本身携带的 {@code tv} 只是用来和数据库里的 {@code token_version} 做比对。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>密钥不足 32 字节直接抛异常，不允许静默使用弱密钥；</li>
 *   <li>解析时校验 issuer 与签名，算法由密钥类型固定，不接受令牌自带的 alg；</li>
 *   <li>过期与无效分开抛错，前端才能区分"重新登录"和"刷新令牌"。</li>
 * </ul>
 */
@Component
public class JwtTokenService {

    /** HS256 要求的最小密钥长度（字节）。 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 允许的时钟偏移，避免容器与签发机之间几秒误差导致刚签发的令牌被判过期。 */
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30L;

    private static final String CLAIM_ROLE = "role";

    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey signingKey;

    private final Duration accessTokenTtl;

    private final String issuer;

    public JwtTokenService(JwtProperties properties) {
        String secret = properties.getSecret();
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret 未配置或长度不足 " + MIN_SECRET_BYTES + " 字节，"
                            + "请通过环境变量 JWT_SECRET 提供至少 32 字节的随机密钥");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenTtl = properties.getAccessTokenTtl();
        this.issuer = properties.getIssuer();
    }

    /**
     * 签发访问令牌。
     *
     * @param accountId    账号 ID
     * @param roleCode     本次生效的活动角色
     * @param tokenVersion 账号当前令牌版本
     * @return 令牌与有效期
     */
    public IssuedToken issue(long accountId, String roleCode, long tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        String tokenId = IdGenerator.nextId();
        String token = Jwts.builder()
                .setId(tokenId)
                .setSubject(String.valueOf(accountId))
                .setIssuer(issuer)
                .claim(CLAIM_ROLE, roleCode)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
        return new IssuedToken(token, accessTokenTtl.toSeconds(), tokenId);
    }

    /**
     * 解析并校验令牌。
     *
     * @param token JWT 字符串
     * @return 令牌载荷
     * @throws BusinessException 令牌过期（{@code AUTH_TOKEN_EXPIRED}）或无效（{@code AUTH_INVALID_TOKEN}）
     */
    public TokenPayload parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer(issuer)
                    .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return new TokenPayload(
                    Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_ROLE, String.class),
                    readTokenVersion(claims),
                    claims.getId(),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            // 签名不匹配、结构损坏、issuer 不符、sub 不是数字，统一按"无效"处理，
            // 不向调用方区分具体原因，避免给伪造令牌提供调试信息
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    /**
     * 读取令牌版本声明。
     *
     * <p>JSON 数字反序列化后可能是 Integer 或 Long，必须按 {@link Number} 读取，
     * 直接强转 Long 会在部分环境抛 ClassCastException。</p>
     */
    private long readTokenVersion(Claims claims) {
        Object raw = claims.get(CLAIM_TOKEN_VERSION);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
    }
}
