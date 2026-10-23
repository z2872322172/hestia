package com.housekeeping.platform.infrastructure.persistence.idempotency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口幂等记录，对应表 {@code idempotency_record}（V1 技术基线）。
 *
 * <p>唯一键 {@code idempotency_key} 是幂等的最终保证：
 * 写接口先插入 PROCESSING 占位，唯一索引冲突说明同一键已被占用。</p>
 */
@Data
@TableName("idempotency_record")
public class IdempotencyRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String idempotencyKey;

    private Long accountId;

    private String apiPath;

    /** 请求内容 SHA-256。 */
    private String requestHash;

    /** 成功响应快照，用于重复请求直接回放。 */
    private String responseJson;

    /** PROCESSING / SUCCEEDED / FAILED。 */
    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
