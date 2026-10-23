package com.housekeeping.platform.infrastructure.storage.minio;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinIO 客户端装配。
 *
 * <p>Access Key / Secret Key 必须来自环境变量。本地开发使用 compose 里的开发凭据，
 * 生产环境由 application-prod.yml 强制从环境变量读取，缺失即启动失败，
 * 绝不允许代码里出现真实密钥。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinioClientConfig {

    /**
     * 创建 MinIO 客户端（线程安全，单例复用）。
     *
     * @param properties 对象存储配置
     * @return MinIO 客户端
     */
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        if (!StringUtils.hasText(properties.getAccessKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            // 提前失败并给出明确原因，避免运行期抛出难以定位的签名异常
            throw new IllegalStateException(
                    "对象存储已启用但未配置访问凭据，请设置 MINIO_ACCESS_KEY / MINIO_SECRET_KEY，"
                            + "或将 app.storage.enabled 设为 false");
        }
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
