-- ============================================================================
-- V1 技术基础表（批次 0）
--
-- 本迁移只创建技术底座表，不创建任何家政业务表：
--   event_outbox         可靠异步事件（Transactional Outbox）
--   consumer_message     消息消费幂等
--   idempotency_record   接口幂等
--   file_object          MinIO 文件元数据
--   audit_log            敏感操作审计（结构取 docs/modules/13 第 14.2 节）
--   system_config        非敏感系统配置（结构取 docs/modules/13 第 14.1 节）
--
-- 约定：
--   * 引擎 InnoDB、字符集 utf8mb4，时间统一 DATETIME(3)，金额统一 DECIMAL；
--   * 主键 BIGINT UNSIGNED 自增，对外标识使用独立业务列（event_id / object_key 等）；
--   * audit_log 与 system_config 在模块 13 的原始 DDL 中带有指向 account 的外键，
--     但 account 表属于 V2（identity 模块）。为避免迁移顺序倒挂，本版先不建该外键，
--     由 V2 在 account 建表后补充（见 V2 中的 ALTER TABLE 说明）。
--   * 已执行的迁移不可修改，后续变更一律新增版本文件。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 可靠事件表：业务事务内写入，由 Outbox Relay 异步投递 RocketMQ
-- ----------------------------------------------------------------------------
CREATE TABLE event_outbox
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id        VARCHAR(32)     NOT NULL COMMENT '事件唯一标识（ULID），消费端幂等键',
    event_type      VARCHAR(64)     NOT NULL COMMENT '事件类型，如 SCAFFOLD_PING',
    aggregate_type  VARCHAR(64)     NOT NULL COMMENT '聚合类型，如 BOOKING_ORDER',
    aggregate_id    VARCHAR(64)     NULL COMMENT '聚合业务 ID，字符串以兼容订单号/工单号',
    trace_id        VARCHAR(64)     NULL COMMENT '产生事件时的链路 ID，便于跨请求与消息排查',
    topic           VARCHAR(128)    NOT NULL COMMENT '目标 Topic',
    tag             VARCHAR(64)     NULL COMMENT '消息 Tag，用于消费端过滤',
    message_key     VARCHAR(128)    NULL COMMENT '消息 Key，建议取聚合 ID 以保证分区有序',
    payload_json    JSON            NOT NULL COMMENT '事件载荷快照，禁止包含完整地址/证件/密钥',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/FAILED/DEAD',
    retry_count     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at   DATETIME(3)     NULL COMMENT '下次可投递时间，实现指数退避',
    last_error      VARCHAR(1000)   NULL COMMENT '最近一次投递失败原因（截断保存）',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    sent_at         DATETIME(3)     NULL COMMENT '投递成功时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_event_id (event_id),
    -- Relay 按 status + next_retry_at 扫描待投递事件，联合索引避免全表扫描
    KEY idx_event_outbox_status_retry (status, next_retry_at),
    KEY idx_event_outbox_aggregate (aggregate_type, aggregate_id),
    CONSTRAINT chk_event_outbox_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='可靠异步事件发件箱';

-- ----------------------------------------------------------------------------
-- 2. 消费幂等表：每个消费者在执行业务逻辑前先插入一条记录
--    唯一键 (consumer_group, message_id) 是"同一消息只处理一次"的最终保证
-- ----------------------------------------------------------------------------
CREATE TABLE consumer_message
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer_group  VARCHAR(64)     NOT NULL COMMENT '消费者组，如 GID_TECH_SMOKE',
    message_id      VARCHAR(128)    NOT NULL COMMENT '消息 ID（RocketMQ MessageId 或事件 eventId）',
    event_type      VARCHAR(64)     NULL COMMENT '事件类型，便于排查',
    aggregate_id    VARCHAR(64)     NULL COMMENT '聚合业务 ID，便于按业务追溯',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCESS/FAILED',
    error_message   VARCHAR(1000)   NULL COMMENT '处理失败原因（截断保存）',
    consumed_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次消费时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '状态变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumer_message_group_message (consumer_group, message_id),
    KEY idx_consumer_message_status (status, consumed_at),
    CONSTRAINT chk_consumer_message_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消息消费幂等记录';

-- ----------------------------------------------------------------------------
-- 3. 接口幂等表：创建订单、支付、取消、改期、退款、评价等写接口共用
--    只保存请求摘要，不保存完整请求体，避免敏感信息落库
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_record
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    idempotency_key VARCHAR(128)    NOT NULL COMMENT '客户端生成的幂等键',
    account_id      BIGINT UNSIGNED NULL COMMENT '发起账号 ID，用于隔离不同用户的同名键',
    api_path        VARCHAR(255)    NOT NULL COMMENT '接口路径，防止同一键跨接口复用',
    request_hash    CHAR(64)        NOT NULL COMMENT '请求内容 SHA-256，内容变化视为冲突',
    response_json   JSON            NULL COMMENT '成功响应快照，用于重复请求直接回放',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCEEDED/FAILED',
    expires_at      DATETIME(3)     NOT NULL COMMENT '记录过期时间，由定时任务清理',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_record_key (idempotency_key),
    KEY idx_idempotency_record_expires (expires_at),
    CONSTRAINT chk_idempotency_record_status
        CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='接口幂等记录';

-- ----------------------------------------------------------------------------
-- 4. 文件元数据表：对象存储里只放字节，权限、归属和业务绑定放这里
--    私有桶文件必须先查本表校验归属，再签发短期预签名 URL
-- ----------------------------------------------------------------------------
CREATE TABLE file_object
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    bucket_name       VARCHAR(64)     NOT NULL COMMENT '桶名，区分公开桶与私有桶',
    object_key        VARCHAR(255)    NOT NULL COMMENT '对象键，使用 UUID，不使用原始文件名',
    original_name     VARCHAR(255)    NULL COMMENT '原始文件名，仅用于展示',
    content_type      VARCHAR(128)    NULL COMMENT 'MIME 类型',
    size_bytes        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件字节数',
    sha256            CHAR(64)        NULL COMMENT '内容摘要，用于秒传与完整性校验',
    visibility        VARCHAR(16)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC/PRIVATE',
    owner_account_id  BIGINT UNSIGNED NULL COMMENT '上传者账号 ID',
    business_type     VARCHAR(64)     NULL COMMENT '业务类型，如 WORKER_CERTIFICATE',
    business_id       VARCHAR(64)     NULL COMMENT '业务主键，绑定具体业务对象',
    status            VARCHAR(16)     NOT NULL DEFAULT 'TEMP' COMMENT 'TEMP/BOUND/DELETED',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_bucket_key (bucket_name, object_key),
    KEY idx_file_object_business (business_type, business_id),
    KEY idx_file_object_owner (owner_account_id, created_at),
    -- 清理任务按 status + created_at 扫描长期未绑定业务的临时文件
    KEY idx_file_object_status (status, created_at),
    CONSTRAINT chk_file_object_visibility
        CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_file_object_status
        CHECK (status IN ('TEMP', 'BOUND', 'DELETED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件对象元数据';

-- ----------------------------------------------------------------------------
-- 5. 审计日志表：只追加，不更新、不删除（结构见 docs/modules/13 第 14.2 节）
--    业务层不得提供修改和删除接口
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id             VARCHAR(64)     NULL COMMENT '链路标识，便于与请求日志对齐',
    operator_account_id  BIGINT UNSIGNED NULL COMMENT '操作账号 ID，系统操作为空',
    operator_role        VARCHAR(24)     NULL COMMENT '操作时的主要角色快照',
    action_type          VARCHAR(64)     NOT NULL COMMENT '操作类型，如 CONFIG_UPDATE',
    business_type        VARCHAR(64)     NOT NULL COMMENT '业务类型，如 SYSTEM_CONFIG',
    business_id          VARCHAR(64)     NULL COMMENT '业务 ID，字符串以兼容多类主键',
    before_json          JSON            NULL COMMENT '操作前摘要',
    after_json           JSON            NULL COMMENT '操作后摘要',
    reason               VARCHAR(1000)   NULL COMMENT '操作原因',
    ip_address           VARCHAR(45)     NULL COMMENT '客户端 IP（兼容 IPv6）',
    user_agent           VARCHAR(512)    NULL COMMENT '客户端 User-Agent',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_audit_log_operator (operator_account_id, created_at),
    KEY idx_audit_log_business (business_type, business_id, created_at),
    KEY idx_audit_log_action (action_type, created_at)
    -- fk_audit_log_operator -> account(id) 在 V2 创建 account 后补充
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='敏感操作审计日志';

-- ----------------------------------------------------------------------------
-- 6. 系统配置表：只放非敏感参数（支付超时、自动确认时长、上传上限等）
--    数据库密码、JWT 密钥、支付密钥、MinIO Secret 一律不允许进入本表
-- ----------------------------------------------------------------------------
CREATE TABLE system_config
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key            VARCHAR(128)    NOT NULL COMMENT '配置键，全局唯一',
    config_name           VARCHAR(128)    NOT NULL COMMENT '配置名称',
    config_group          VARCHAR(64)     NOT NULL DEFAULT 'GENERAL' COMMENT '配置分组',
    value_type            VARCHAR(16)     NOT NULL COMMENT 'STRING/INTEGER/DECIMAL/BOOLEAN/JSON',
    value_json            JSON            NOT NULL COMMENT '配置值，统一 JSON 编码',
    description           VARCHAR(500)    NULL COMMENT '配置说明',
    status                VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key),
    KEY idx_system_config_group (config_group, status),
    CONSTRAINT chk_system_config_type
        CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON')),
    CONSTRAINT chk_system_config_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
    -- fk_system_config_updater -> account(id) 在 V2 创建 account 后补充
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='非敏感系统配置';
