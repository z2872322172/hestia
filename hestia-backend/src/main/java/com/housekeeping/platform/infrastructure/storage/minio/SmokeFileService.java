package com.housekeeping.platform.infrastructure.storage.minio;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;

/**
 * 技术冒烟文件服务：验证"上传 → 落元数据 → 生成短期访问地址"链路。
 *
 * <p>与业务文件服务分开：这里不做业务权限判断，只做技术校验，
 * 避免骨架代码被误当成可用的业务上传接口。</p>
 */
@Service
@ConditionalOnExpression("${app.feature.persistence-enabled:true} and ${app.storage.enabled:true}")
public class SmokeFileService {

    private static final Logger LOG = LoggerFactory.getLogger(SmokeFileService.class);

    /** 白名单：只允许图片与 PDF（docs/04 第 6.5 节）。 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    private static final DateTimeFormatter DATE_DIRECTORY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectStoragePort objectStoragePort;

    private final MinioProperties minioProperties;

    private final FileObjectMapper fileObjectMapper;

    public SmokeFileService(ObjectStoragePort objectStoragePort,
                            MinioProperties minioProperties,
                            FileObjectMapper fileObjectMapper) {
        this.objectStoragePort = objectStoragePort;
        this.minioProperties = minioProperties;
        this.fileObjectMapper = fileObjectMapper;
    }

    /**
     * 上传一个冒烟文件到公开桶，并写入元数据。
     *
     * @param file 上传文件
     * @return 上传结果，包含对象键、摘要和短期访问地址
     */
    public SmokeFileResult upload(MultipartFile file) {
        validate(file);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "读取上传文件失败", exception);
        }

        String contentType = file.getContentType();
        String objectKey = buildObjectKey(contentType);
        String bucket = minioProperties.getPublicBucket();

        objectStoragePort.putObject(bucket, objectKey,
                new ByteArrayInputStream(content), content.length, contentType);

        FileObject metadata = new FileObject();
        metadata.setBucketName(bucket);
        metadata.setObjectKey(objectKey);
        // 原始文件名可能包含用户信息，仅用于展示，不参与对象键
        metadata.setOriginalName(file.getOriginalFilename());
        metadata.setContentType(contentType);
        metadata.setSizeBytes((long) content.length);
        metadata.setSha256(sha256Hex(content));
        metadata.setVisibility("PUBLIC");
        metadata.setBusinessType("SCAFFOLD_SMOKE");
        metadata.setBusinessId(null);
        // 冒烟文件没有业务归属，保持 TEMP，由后续清理任务回收
        metadata.setStatus("TEMP");
        metadata.setDeleted(0);
        metadata.setCreatedAt(LocalDateTime.now());
        fileObjectMapper.insert(metadata);

        Duration expiry = Duration.ofSeconds(minioProperties.getPresignedExpirySeconds());
        String url = objectStoragePort.presignedGetUrl(bucket, objectKey, expiry);
        LOG.info("冒烟文件上传完成 bucket={} objectKey={} size={}", bucket, objectKey, content.length);

        return new SmokeFileResult(bucket, objectKey, content.length, metadata.getSha256(), url,
                minioProperties.getPresignedExpirySeconds());
    }

    /**
     * 校验文件类型、大小和文件头。
     *
     * <p>只信任扩展名是不够的：把可执行文件改名成 .jpg 一样能上传成功。
     * 这里用文件头做一次轻量校验，生产环境还应结合病毒扫描。</p>
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > minioProperties.getMaxUploadSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID,
                    "文件大小超过限制：" + minioProperties.getMaxUploadSizeBytes() + " 字节");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "仅支持 JPEG、PNG、PDF 文件");
        }
        byte[] header;
        try {
            header = file.getInputStream().readNBytes(8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "读取文件头失败", exception);
        }
        if (!matchesMagicNumber(contentType, header)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "文件内容与声明类型不一致");
        }
    }

    private boolean matchesMagicNumber(String contentType, byte[] header) {
        return switch (contentType) {
            // JPEG: FF D8 FF
            case "image/jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            case "image/png" -> header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            // PDF: %PDF
            case "application/pdf" -> header.length >= 4
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
            default -> false;
        };
    }

    /**
     * 生成对象键：按日期分目录 + ULID + 扩展名。
     *
     * <p>不使用原始文件名作为对象键，避免路径穿越、重名覆盖和中文字符编码问题。</p>
     */
    private String buildObjectKey(String contentType) {
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".pdf";
        };
        return "smoke/" + LocalDate.now().format(DATE_DIRECTORY) + "/" + IdGenerator.nextId() + extension;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            // JDK 必须提供 SHA-256，走到这里说明运行环境异常
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    /**
     * 冒烟上传结果。
     *
     * @param bucket           桶名
     * @param objectKey        对象键
     * @param sizeBytes        文件字节数
     * @param sha256           内容摘要
     * @param url              短期访问地址
     * @param expiresInSeconds 地址有效期（秒）
     */
    public record SmokeFileResult(String bucket, String objectKey, long sizeBytes, String sha256,
                                  String url, int expiresInSeconds) {
    }
}
