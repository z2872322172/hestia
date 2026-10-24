-- ============================================================================
-- V8 通知模块（docs/modules/12）
-- 说明：docs/07 原计划的 V7__quality_notification.sql 版本号已被
--       V7__dispatch_reason.sql 占用（评价/售后表实际随 V6 落地），
--       通知迁移顺延为 V8，运营日报/导出后续使用 V9。
-- 与设计文档的差异：notification_template 增加 audience 列——
--       同一事件面向客户与人员的文案不同（如"支付成功"与"新任务通知"），
--       收件人解析按（事件类型 + 受众）选模板，dedupe_key 已含 template_code，
--       该列是模板选择的必要键而非冗余。
-- ============================================================================

CREATE TABLE notification_template (
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    audience VARCHAR(16) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    title_template VARCHAR(255) NOT NULL,
    content_template TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 1,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (template_code),
    KEY idx_notification_template_event
        (event_type, audience, channel, status),
    CONSTRAINT chk_notification_template_channel
        CHECK (channel IN ('IN_APP', 'SMS', 'WECHAT', 'EMAIL', 'WEBSOCKET')),
    CONSTRAINT chk_notification_template_audience
        CHECK (audience IN ('CUSTOMER', 'WORKER', 'OPERATOR')),
    CONSTRAINT chk_notification_template_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_notification_template_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='通知模板';

CREATE TABLE notification_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(32) NOT NULL,
    source_event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    recipient_account_id BIGINT UNSIGNED NOT NULL,
    recipient_role VARCHAR(24) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
    payload_json JSON NOT NULL,
    dedupe_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_retry_count INT UNSIGNED NOT NULL DEFAULT 5,
    next_retry_at DATETIME(3) NULL,
    last_error VARCHAR(500) NULL,
    scheduled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_task_no (task_no),
    UNIQUE KEY uk_notification_task_dedupe (dedupe_key),
    KEY idx_notification_task_pending
        (status, next_retry_at, scheduled_at),
    KEY idx_notification_task_recipient
        (recipient_account_id, created_at),
    CONSTRAINT fk_notification_task_template
        FOREIGN KEY (template_code) REFERENCES notification_template (template_code)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_notification_task_account
        FOREIGN KEY (recipient_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_notification_task_channel
        CHECK (channel IN ('IN_APP', 'SMS', 'WECHAT', 'EMAIL', 'WEBSOCKET')),
    CONSTRAINT chk_notification_task_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELED')),
    CONSTRAINT chk_notification_task_retry
        CHECK (retry_count <= max_retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='异步通知任务';

CREATE TABLE notification_delivery (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    attempt_no INT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_message_id VARCHAR(128) NULL,
    error_message VARCHAR(500) NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_delivery_attempt
        (task_id, channel, attempt_no),
    KEY idx_notification_delivery_status
        (status, created_at),
    CONSTRAINT fk_notification_delivery_task
        FOREIGN KEY (task_id) REFERENCES notification_task (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_notification_delivery_channel
        CHECK (channel IN ('IN_APP', 'SMS', 'WECHAT', 'EMAIL', 'WEBSOCKET')),
    CONSTRAINT chk_notification_delivery_status
        CHECK (status IN ('SENDING', 'SENT', 'FAILED', 'SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='通知渠道发送记录';

CREATE TABLE notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_no VARCHAR(32) NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    recipient_account_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    business_type VARCHAR(32) NULL,
    business_id VARCHAR(64) NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    read_at DATETIME(3) NULL,
    expires_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_no (notification_no),
    UNIQUE KEY uk_notification_task (task_id),
    KEY idx_notification_inbox
        (recipient_account_id, read_at, created_at),
    CONSTRAINT fk_notification_task
        FOREIGN KEY (task_id) REFERENCES notification_task (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_notification_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='站内通知';

-- ---------------------------------------------------------------------------
-- 种子模板：订单事件族（BOOKING_*），变量只用业务编号与脱敏字段
-- （docs/modules/12 第 8 节：禁止完整地址、手机号、证件号、支付凭证、照片 URL）
-- ---------------------------------------------------------------------------

-- 变量与 BookingEventPublisher 载荷对齐：orderNo / serviceDate / startTime
-- （serviceDate、startTime 由 NotificationTaskService 从 appointmentDate /
--   appointmentStart 归一化而来，避免 LocalDateTime 的 'T' 直出到正文）

INSERT INTO notification_template
    (template_code, template_name, event_type, audience, channel,
     title_template, content_template, priority)
VALUES
    ('NTPL_BKG_CREATED_CUS', '下单成功-客户', 'BOOKING_CREATED', 'CUSTOMER', 'IN_APP',
     '订单已提交', '订单 {orderNo} 已提交，请尽快完成支付。',
     'NORMAL'),
    ('NTPL_BKG_PAID_CUS', '支付成功-客户', 'BOOKING_PAID', 'CUSTOMER', 'IN_APP',
     '支付成功', '订单 {orderNo} 支付成功，服务安排在 {serviceDate} {startTime}。',
     'NORMAL'),
    ('NTPL_BKG_PAID_WRK', '新任务-人员', 'BOOKING_PAID', 'WORKER', 'IN_APP',
     '收到新任务', '您有一个新服务任务（订单 {orderNo}），服务时间 {serviceDate} {startTime}，请及时确认。',
     'HIGH'),
    ('NTPL_BKG_RESCHED_CUS', '改期成功-客户', 'BOOKING_RESCHEDULED', 'CUSTOMER', 'IN_APP',
     '订单已改期', '订单 {orderNo} 已改期至 {serviceDate}。',
     'NORMAL'),
    ('NTPL_BKG_CANCELED_CUS', '订单取消-客户', 'BOOKING_CANCELED', 'CUSTOMER', 'IN_APP',
     '订单已取消', '订单 {orderNo} 已取消，如有疑问请联系客服。',
     'NORMAL'),
    ('NTPL_BKG_CANCELED_WRK', '任务取消-人员', 'BOOKING_CANCELED', 'WORKER', 'IN_APP',
     '任务已取消', '订单 {orderNo} 的服务任务已取消，无需上门。',
     'NORMAL'),
    ('NTPL_BKG_COMPLETED_CUS', '服务完成-客户', 'BOOKING_COMPLETED', 'CUSTOMER', 'IN_APP',
     '服务已完成', '订单 {orderNo} 的服务已完成，欢迎对本次服务进行评价。',
     'NORMAL'),
    ('NTPL_BKG_COMPLETED_WRK', '服务完成-人员', 'BOOKING_COMPLETED', 'WORKER', 'IN_APP',
     '服务已完成', '订单 {orderNo} 的服务已确认完成。',
     'NORMAL');
