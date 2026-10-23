package com.housekeeping.platform.integration;

import com.housekeeping.platform.infrastructure.storage.minio.MinioObjectStorageAdapter;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MinIO 冒烟测试：验证建桶、上传、存在性检查与预签名地址生成。
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MinioSmokeTest {

    private static final String ACCESS_KEY = "minioadmin";

    private static final String SECRET_KEY = "minioadmin";

    private static final String BUCKET = "housekeeping-test-public";

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2024-12-18T13-15-44Z"))
                    .withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                    .withCommand("server", "/data")
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @Test
    @DisplayName("对象可上传、可检查存在，并能生成预签名地址")
    void uploadsObjectAndGeneratesPresignedUrl() {
        MinioClient client = MinioClient.builder()
                .endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        MinioObjectStorageAdapter adapter = new MinioObjectStorageAdapter(client);

        adapter.ensureBucket(BUCKET);

        byte[] content = "hestia-smoke".getBytes(StandardCharsets.UTF_8);
        adapter.putObject(BUCKET, "smoke/test.txt",
                new ByteArrayInputStream(content), content.length, "text/plain");

        assertThat(adapter.objectExists(BUCKET, "smoke/test.txt")).isTrue();
        assertThat(adapter.presignedGetUrl(BUCKET, "smoke/test.txt", Duration.ofMinutes(5)))
                .contains("X-Amz-Signature");
        assertThat(adapter.storageName()).isEqualTo("minio");
    }
}
