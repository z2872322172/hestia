package com.housekeeping.platform.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 *
 * <p>文档标题与说明写清楚"当前只有系统集成接口"，避免答辩或联调时把 smoke 接口
 * 误当成业务能力。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 构建 OpenAPI 元信息。
     *
     * @return OpenAPI 定义
     */
    @Bean
    public OpenAPI hestiaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Hestia 家政服务预约平台 API")
                .version("v1")
                .description("""
                        单公司自营家政服务预约平台后端。
                        当前处于技术底座阶段：只提供系统自检接口（ping / integrations / smoke），
                        不包含账号、订单、支付等业务接口。
                        """));
    }
}
