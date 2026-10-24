package com.housekeeping.platform.bootstrap.config;

import com.housekeeping.platform.identity.api.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置。
 *
 * <p>授权策略按路径前缀划分（docs/modules/01 第 8.2 节）：</p>
 * <ul>
 *   <li>{@code /api/v1/auth/**} 中的注册、登录、激活是公共接口；</li>
 *   <li>{@code /api/v1/public/**} 是位置等公开查询；</li>
 *   <li>{@code /api/v1/customer/**} 要求 CUSTOMER 角色；</li>
 *   <li>{@code /api/v1/worker/**} 要求 WORKER 角色；</li>
 *   <li>{@code /api/v1/operator/**} 要求 OPERATOR 或 ADMIN；</li>
 *   <li>{@code /api/v1/admin/**} 要求 ADMIN；</li>
 *   <li>其余一律要求已认证。</li>
 * </ul>
 *
 * <p>这里只做"接口级"权限；数据范围（SELF / AREA / ALL）在应用服务层校验，
 * 因为路径无法表达"这条订单是不是我的"。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    private final RestAccessDeniedHandler accessDeniedHandler;

    private final boolean smokeApiEnabled;

    private final String[] corsAllowedOrigins;

    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilter;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Value("${app.security.smoke-api-enabled:false}") boolean smokeApiEnabled,
                          @Value("${app.security.cors-allowed-origins}") String corsAllowedOrigins,
                          ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilter) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.smokeApiEnabled = smokeApiEnabled;
        this.corsAllowedOrigins = corsAllowedOrigins.split("\\s*,\\s*");
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 安全过滤器链。
     *
     * <p>无状态 + 关闭 CSRF：令牌放在 Authorization 头里，不依赖 Cookie，
     * 因此不存在 CSRF 攻击面。一旦将来改用 Cookie 存放令牌，必须同时打开 CSRF 防护。</p>
     *
     * @param http HttpSecurity 构建器
     * @return 过滤器链
     * @throws Exception 构建失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> {
                    // 健康检查与接口文档：运维和联调必需
                    registry.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    registry.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    // WebSocket 握手：CONNECT 帧的令牌校验在实时通道批次补齐
                    registry.requestMatchers("/ws", "/ws/**").permitAll();
                    // 系统自检接口：只暴露应用名、profile 和集成状态，不含任何业务数据
                    registry.requestMatchers("/api/v1/system/ping", "/api/v1/system/integrations").permitAll();
                    if (smokeApiEnabled) {
                        registry.requestMatchers("/api/v1/system/smoke/**").permitAll();
                    } else {
                        // 生产环境显式拒绝，而不是"依赖不注册接口"
                        registry.requestMatchers("/api/v1/system/smoke/**").denyAll();
                    }
                    // 公共认证入口：注册、登录、服务人员激活
                    registry.requestMatchers("/api/v1/auth/customer/register",
                            "/api/v1/auth/login",
                            "/api/v1/auth/worker/activate").permitAll();
                    // 公开位置查询
                    registry.requestMatchers("/api/v1/public/**").permitAll();
                    // 支付渠道回调：外部网关调用，不带平台令牌；防伪依赖签名校验 + 幂等占位
                    registry.requestMatchers("/api/v1/payment/callback/**").permitAll();
                    // 按角色划分的业务前缀
                    registry.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    registry.requestMatchers("/api/v1/operator/**").hasAnyRole("OPERATOR", "ADMIN");
                    registry.requestMatchers("/api/v1/customer/**").hasRole("CUSTOMER");
                    // 服务人员只能看自己的档案/技能/证书/区域；WORKER 角色被暂停时这里立即 403，
                    // 这正是 docs/modules/05 第 11.1 节"暂停人员优先暂停 WORKER 角色"的落点
                    registry.requestMatchers("/api/v1/worker/**").hasRole("WORKER");
                    registry.anyRequest().authenticated();
                })
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        // 无持久层时（单元测试 profile）过滤器不存在，此时不注册，上下文仍可加载
        JwtAuthenticationFilter filter = jwtAuthenticationFilter.getIfAvailable();
        if (filter != null) {
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * 跨域配置。
     *
     * <p>只允许配置中列出的前端源，不使用通配符：携带凭证的跨域请求不能用 {@code *}。</p>
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(corsAllowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 密码编码器。
     *
     * <p>强度参数用 12（docs/modules/01 第 7.1 节要求至少 10、建议 12）。
     * 12 比默认 10 慢约 4 倍，是"被拖库后爆破成本"与"登录耗时"之间更合理的取舍。</p>
     *
     * @return BCrypt 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
