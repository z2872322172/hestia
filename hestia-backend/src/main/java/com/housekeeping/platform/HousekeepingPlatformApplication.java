package com.housekeeping.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hestia 家政服务预约平台后端启动类。
 *
 * <p>本项目是<b>模块化单体</b>：单个 Spring Boot 应用、单个 Maven 工程，按业务包垂直划分模块。
 * 启动类只负责装配，不承载任何业务逻辑。</p>
 *
 * <p>基础设施能力（MySQL、Redis、RocketMQ、MinIO、地图、WebSocket）全部通过
 * {@code app.*} 配置开关控制，未就绪时不阻塞应用启动，便于本地逐步搭建环境。</p>
 *
 * <p>排除 {@link UserDetailsServiceAutoConfiguration}：本项目的认证走 JWT 过滤器，
 * 不使用 Spring Security 的 {@code UserDetailsService}。不排除的话，Spring Boot 会在启动日志里
 * 打印一个随机生成的默认密码并注册 {@code inMemoryUserDetailsManager}——那个用户永远用不上，
 * 却会让人误以为系统存在一个"默认管理员"。</p>
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HousekeepingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(HousekeepingPlatformApplication.class, args);
    }
}
