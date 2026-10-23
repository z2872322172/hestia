package com.housekeeping.platform.infrastructure.storage.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置。
 *
 * <p>公开桶只放服务封面、分类图标这类可匿名读取的资源；
 * 证件、健康证明、履约照片一律进私有桶，通过短期预签名 URL 访问。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.storage")
public class MinioProperties {

    /** 是否启用对象存储。关闭后不创建 MinIO 客户端，文件相关接口不装配。 */
    private boolean enabled = true;

    private String endpoint = "http://localhost:9000";

    private String accessKey;

    private String secretKey;

    /** 公开桶：允许匿名读取。 */
    private String publicBucket = "housekeeping-dev-public";

    /** 私有桶：禁止匿名访问。 */
    private String privateBucket = "housekeeping-dev-private";

    /** 预签名 URL 有效期（秒），建议 300 至 900。 */
    private int presignedExpirySeconds = 900;

    /** 单文件大小上限（字节），默认 20MB。 */
    private long maxUploadSizeBytes = 20L * 1024 * 1024;
}
