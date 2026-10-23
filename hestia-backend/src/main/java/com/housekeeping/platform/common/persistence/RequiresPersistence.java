package com.housekeeping.platform.common.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记"必须依赖数据库"的组件。
 *
 * <p>当 {@code app.feature.persistence-enabled=false}（单元测试、契约测试）时，
 * Mapper 不会被 {@code @MapperScan} 注册，注入 Mapper 的 Bean 会直接让上下文加载失败。
 * 因此业务模块的 Service / Controller / 安全过滤器统一打上本注解，
 * 让整个业务层随持久层一起退出，而不是逐个手写 {@code @ConditionalOnProperty}。</p>
 *
 * <p>这是元注解用法：{@link ConditionalOnProperty} 的 {@code @Target} 包含 TYPE，
 * 而 TYPE 涵盖注解声明，因此可以安全地作为元注解复用。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
public @interface RequiresPersistence {
}
