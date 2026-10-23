package com.housekeeping.platform.infrastructure.storage.minio;

import java.io.InputStream;
import java.time.Duration;

/**
 * 对象存储端口。
 *
 * <p>业务代码只依赖本接口，不直接依赖 MinIO SDK：
 * 未来换成 S3、OSS 或本地磁盘时，只需要新增一个实现。</p>
 */
public interface ObjectStoragePort {

    /**
     * 上传对象。
     *
     * @param bucket      桶名
     * @param objectKey   对象键，必须由服务端生成，禁止使用用户传入的原始文件名
     * @param inputStream 数据流
     * @param sizeBytes   字节数，MinIO 需要显式长度才能正确设置元数据
     * @param contentType MIME 类型
     */
    void putObject(String bucket, String objectKey, InputStream inputStream, long sizeBytes, String contentType);

    /**
     * 判断对象是否存在。
     *
     * @param bucket    桶名
     * @param objectKey 对象键
     * @return 存在返回 {@code true}
     */
    boolean objectExists(String bucket, String objectKey);

    /**
     * 生成下载用的预签名 URL。
     *
     * <p>私有桶的 URL 有效期必须很短（分钟级），且每次访问前都要做业务权限校验。</p>
     *
     * @param bucket    桶名
     * @param objectKey 对象键
     * @param expiry    有效期
     * @return 预签名 URL
     */
    String presignedGetUrl(String bucket, String objectKey, Duration expiry);

    /**
     * 确保桶存在（不存在则创建）。
     *
     * @param bucket 桶名
     */
    void ensureBucket(String bucket);

    /**
     * 当前实现的名称，用于健康检查与日志。
     *
     * @return 实现名，如 {@code minio}
     */
    String storageName();
}
