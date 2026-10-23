package com.housekeeping.platform.bootstrap.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.housekeeping.platform.common.persistence.AuditMetaObjectHandler;
import com.housekeeping.platform.common.security.CurrentAccountProvider;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 装配。
 *
 * <p>约束：</p>
 * <ul>
 *   <li>只做 CRUD、分页和乐观锁，不负责建表——表结构唯一入口是 Flyway；</li>
 *   <li>只扫描标注 {@link Mapper} 的接口，避免把 Port 接口误当成 Mapper 生成代理；</li>
 *   <li>{@code app.feature.persistence-enabled=false} 时不装配任何依赖数据源的 Bean，
 *       使无数据库环境也能加载上下文做契约测试。</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.feature", name = "persistence-enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackages = "com.housekeeping.platform", annotationClass = Mapper.class)
public class MybatisPlusConfig {

    /**
     * 分页与乐观锁插件。
     *
     * <p>分页必须在数据库完成，禁止全表查询后内存分页；
     * 乐观锁插件保证 {@code version} 字段在并发更新时只允许一个请求成功。</p>
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限与 PageQuery 保持一致，双重保险防止绕过接口直接传大 size
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 审计字段填充器。
     *
     * @param currentAccountProvider 当前账号提供者
     * @return 元对象处理器
     */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler(CurrentAccountProvider currentAccountProvider) {
        return new AuditMetaObjectHandler(currentAccountProvider);
    }
}
