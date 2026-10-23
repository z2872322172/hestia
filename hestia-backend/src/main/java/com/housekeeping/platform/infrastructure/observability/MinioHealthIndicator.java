package com.housekeeping.platform.infrastructure.observability;

import com.housekeeping.platform.infrastructure.storage.minio.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MinIO 健康检查，注册为 {@code /actuator/health} 中的 {@code minio} 组件。
 *
 * <p>只检查两个业务桶是否存在，不上传测试对象：健康检查不应产生副作用，
 * 也不应该每次都写一条垃圾数据。</p>
 */
@Component("minioHealthIndicator")
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;

    private final MinioProperties properties;

    public MinioHealthIndicator(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            boolean publicBucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getPublicBucket()).build());
            boolean privateBucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getPrivateBucket()).build());
            Health.Builder builder = publicBucketExists && privateBucketExists ? Health.up() : Health.down();
            return builder
                    .withDetail("endpoint", properties.getEndpoint())
                    .withDetail("buckets", List.of(properties.getPublicBucket(), properties.getPrivateBucket()))
                    .withDetail("publicBucketExists", publicBucketExists)
                    .withDetail("privateBucketExists", privateBucketExists)
                    .build();
        } catch (Exception exception) {
            // 异常类型足以定位问题，不输出 endpoint 响应体等内部细节
            return Health.down()
                    .withDetail("endpoint", properties.getEndpoint())
                    .withDetail("error", exception.getClass().getSimpleName())
                    .build();
        }
    }
}
