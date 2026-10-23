package com.housekeeping.platform.infrastructure.storage.minio;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储适配器。
 *
 * <p>异常处理原则：MinIO 不可用属于"外部依赖故障"，统一转换成
 * {@link ErrorCode#INTEGRATION_UNAVAILABLE}，避免把 SDK 异常和内部端点泄露给客户端。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinioObjectStorageAdapter implements ObjectStoragePort {

    private static final Logger LOG = LoggerFactory.getLogger(MinioObjectStorageAdapter.class);

    private static final String STORAGE_NAME = "minio";

    private final MinioClient minioClient;

    public MinioObjectStorageAdapter(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream, long sizeBytes, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, sizeBytes, -1)
                    .contentType(contentType)
                    .build());
            LOG.info("对象上传成功 bucket={} objectKey={} size={}", bucket, objectKey, sizeBytes);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "文件上传失败，请稍后重试", exception);
        }
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (ErrorResponseException exception) {
            // NoSuchKey 是"不存在"的正常分支，不是故障
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                return false;
            }
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "对象存储不可用", exception);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "对象存储不可用", exception);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "生成文件访问地址失败", exception);
        }
    }

    @Override
    public void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                LOG.info("创建对象存储桶 bucket={}", bucket);
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "对象存储桶不可用", exception);
        }
    }

    @Override
    public String storageName() {
        return STORAGE_NAME;
    }
}
