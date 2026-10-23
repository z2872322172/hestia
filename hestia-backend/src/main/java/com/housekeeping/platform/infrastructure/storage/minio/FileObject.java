package com.housekeeping.platform.infrastructure.storage.minio;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件对象元数据实体，对应表 {@code file_object}。
 *
 * <p>对象存储只保存字节，权限与归属必须落库：
 * "谁能看这个文件"由 {@code owner_account_id + business_type + business_id + visibility}
 * 共同决定，而不是靠对象键猜。</p>
 */
@Data
@TableName("file_object")
public class FileObject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bucketName;

    private String objectKey;

    private String originalName;

    private String contentType;

    private Long sizeBytes;

    private String sha256;

    /** PUBLIC / PRIVATE。 */
    private String visibility;

    private Long ownerAccountId;

    private String businessType;

    private String businessId;

    /** TEMP / BOUND / DELETED。 */
    private String status;

    /** 逻辑删除标记，由 MyBatis-Plus 全局配置接管。 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
