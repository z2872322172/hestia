-- ============================================================================
-- V9 运营模块（批次 5 / docs/modules/13 第 14.3、14.4 节）
--
-- 说明：
--   * audit_log 与 system_config 已在 V1（技术底座）按模块 13 的表结构建好，
--     指向 account 的外键由 V2 补齐，本迁移不重复创建；
--   * docs/07 原计划用 V8 承载运营表，V8 已被通知模块占用，运营顺延为 V9；
--   * operation_daily_metric 是"按日聚合读模型"：由日报任务从各业务模块的
--     公开统计契约聚合写入，可重复重算（唯一键 UPSERT），不替代业务事实表；
--   * export_job 的导出文件通过私有对象存储（file_object + MinIO）落地，
--     到期由清理任务删除，禁止公开 URL。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 按日区域经营指标（读模型，docs/modules/13 第 14.3 节）
--    service_area_id = 0 表示全公司汇总；(metric_date, service_area_id) 唯一，
--    日报任务按该键 UPSERT，重复执行结果一致（幂等重算）。
-- ----------------------------------------------------------------------------
CREATE TABLE operation_daily_metric
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    metric_date            DATE            NOT NULL COMMENT '统计日期（业务口径：订单创建日 / 事件发生日）',
    service_area_id        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '服务区域 ID，0 表示全公司',
    orders_created         INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '订单创建数',
    orders_paid            INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '支付成功订单数',
    orders_completed       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '完成订单数',
    orders_canceled        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '取消订单数',
    orders_rescheduled     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '改期次数',
    paid_amount            DECIMAL(14, 2)  NOT NULL DEFAULT 0.00 COMMENT '支付金额',
    refund_amount          DECIMAL(14, 2)  NOT NULL DEFAULT 0.00 COMMENT '退款金额',
    auto_assignment_success INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '自动派单接单成功数',
    assignment_rejected    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '拒单数',
    assignment_timeout     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '接单超时数',
    manual_assignment_count INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '人工派单数',
    worker_slot_total      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '可用时段总数（全公司口径）',
    worker_slot_booked     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已占用时段数（全公司口径）',
    complaint_count        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '投诉 / 售后工单数',
    rework_count           INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '返工工单数',
    late_count             INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '迟到次数（首版无准时率数据源，保留列）',
    metric_version         INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '统计口径版本，口径变更时递增',
    created_by_account_id  BIGINT UNSIGNED NULL,
    updated_by_account_id  BIGINT UNSIGNED NULL,
    version                BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重算次数（乐观锁）',
    calculated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本次计算时间',
    created_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_daily_metric (metric_date, service_area_id),
    KEY idx_operation_daily_metric_area (service_area_id, metric_date),
    CONSTRAINT chk_operation_daily_metric_area
        CHECK (service_area_id >= 0),
    CONSTRAINT chk_operation_daily_metric_amount
        CHECK (paid_amount >= 0 AND refund_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='按日区域经营指标（读模型）';

-- ----------------------------------------------------------------------------
-- 2. 数据导出任务（docs/modules/13 第 14.4 节）
--    敏感数据导出必须异步、可审计、有过期时间；失败记录错误原因，
--    不生成空文件冒充成功。文件经私有对象存储下载（短期签名 URL）。
-- ----------------------------------------------------------------------------
CREATE TABLE export_job
(
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    export_no               VARCHAR(32)     NOT NULL COMMENT '导出业务编号，形如 EXP20261024000001',
    export_type             VARCHAR(32)     NOT NULL COMMENT '导出类型：ORDERS/WORKERS/PAYMENTS/AFTER_SALES/AUDIT',
    query_json              JSON            NOT NULL COMMENT '查询条件快照（日期区间、区域等），禁止含敏感明文',
    status                  VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/EXPIRED/CANCELED',
    requested_by_account_id BIGINT UNSIGNED NOT NULL COMMENT '发起账号 ID',
    file_id                 BIGINT UNSIGNED NULL COMMENT 'file_object 主键，生成成功后回填',
    row_count               INT UNSIGNED    NULL COMMENT '导出行数',
    error_message           VARCHAR(500)    NULL COMMENT '失败原因（截断保存）',
    expires_at              DATETIME(3)     NULL COMMENT '文件过期时间，到期清理',
    started_at              DATETIME(3)     NULL,
    finished_at             DATETIME(3)     NULL,
    created_by_account_id   BIGINT UNSIGNED NULL,
    updated_by_account_id   BIGINT UNSIGNED NULL,
    version                 BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_export_job_no (export_no),
    KEY idx_export_job_requester (requested_by_account_id, created_at),
    KEY idx_export_job_status (status, created_at),
    CONSTRAINT fk_export_job_requester
        FOREIGN KEY (requested_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_export_job_type
        CHECK (export_type IN ('ORDERS', 'WORKERS', 'PAYMENTS', 'AFTER_SALES', 'AUDIT')),
    CONSTRAINT chk_export_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'EXPIRED', 'CANCELED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='数据导出任务';

-- ----------------------------------------------------------------------------
-- 3. 系统配置种子（docs/modules/13 第 10 节建议配置项）
--    仅保存"非敏感"参数；数据库密码、JWT 密钥、支付密钥、MinIO Secret 一律不入库。
--    当前业务模块的运行时取值仍来自 application.yml / 派单策略表，本表是
--    运营侧的统一配置台账：修改走管理端接口并写审计，后续模块逐步切换为读这里。
-- ----------------------------------------------------------------------------
INSERT INTO system_config (config_key, config_name, config_group, value_type, value_json, description, status)
VALUES
    ('payment_timeout_minutes', '支付超时分钟数', 'BOOKING', 'INTEGER', '15',
     '订单待支付截止时长，参考值与 app.booking.payment-timeout-minutes 保持一致', 'ACTIVE'),
    ('assignment_accept_timeout_minutes', '接单超时分钟数', 'DISPATCH', 'INTEGER', '10',
     '人员接单截止时长，参考值与派单策略 POL_DEFAULT 保持一致', 'ACTIVE'),
    ('auto_confirm_hours', '自动确认小时数', 'FULFILLMENT', 'INTEGER', '24',
     '履约完成后客户未确认的自动确认时长', 'ACTIVE'),
    ('reschedule_cutoff_hours', '改期截止小时数', 'BOOKING', 'INTEGER', '24',
     '服务开始前允许改期的最晚小时数', 'ACTIVE'),
    ('free_cancel_hours', '免费取消小时数', 'BOOKING', 'INTEGER', '24',
     '服务开始前免费取消窗口，参考值与 app.booking.free-cancel-hours 保持一致', 'ACTIVE'),
    ('late_cancel_fee_rate', '临近取消费率', 'BOOKING', 'DECIMAL', '0.3',
     '免费窗口外取消的费率，与固定金额取较大者', 'ACTIVE'),
    ('max_orders_per_worker_day', '人员每日接单上限', 'WORKFORCE', 'INTEGER', '5',
     '同一人员单日最多可接订单数', 'ACTIVE'),
    ('certificate_expiry_notice_days', '证书到期提醒天数', 'WORKFORCE', 'INTEGER', '30',
     '证书到期前 N 天进入运营工作台预警', 'ACTIVE'),
    ('address_limit_per_customer', '客户地址上限', 'CUSTOMER', 'INTEGER', '20',
     '每个客户有效地址数量上限，参考值与 app.customer.address-limit 保持一致', 'ACTIVE'),
    ('max_upload_size_bytes', '上传大小上限（字节）', 'GENERAL', 'INTEGER', '20971520',
     '单文件上传上限，参考值与 app.storage.max-upload-size-bytes 保持一致', 'ACTIVE');
