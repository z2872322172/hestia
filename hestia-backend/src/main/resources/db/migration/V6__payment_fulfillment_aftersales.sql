-- ============================================================================
-- V6 支付退款 / 履约证据 / 服务质量与售后
-- （docs/modules/10、docs/modules/09、docs/modules/11）
--
-- payment_record / refund_record / payment_callback_log
-- service_record / service_checklist_item / service_evidence / service_exception
-- review_dimension / service_review / review_dimension_score / review_evidence /
-- after_sale_ticket / after_sale_event / after_sale_attachment / worker_quality_stat
--
-- 跨模块约定：booking_id / worker_id / file_id / payment_id 等均为逻辑外键，
-- 仅指向 account 的列保留物理外键；状态流转一律条件更新。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 支付记录（success_booking_id 生成列唯一：一单最多一条成功支付）
-- ---------------------------------------------------------------------------
CREATE TABLE payment_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(32) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    attempt_no INT NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'MOCK',
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    third_party_trade_no VARCHAR(128) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    paid_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_booking_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status = 'SUCCESS' THEN booking_id ELSE NULL END
        ) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_record_no (payment_no),
    UNIQUE KEY uk_payment_record_attempt (booking_id, attempt_no),
    UNIQUE KEY uk_payment_record_idempotency (account_id, idempotency_key),
    UNIQUE KEY uk_payment_record_success_booking (success_booking_id),
    UNIQUE KEY uk_payment_record_trade_no (third_party_trade_no),
    KEY idx_payment_record_booking (booking_id, status),
    KEY idx_payment_record_account (account_id, created_at),
    CONSTRAINT fk_payment_record_account
        FOREIGN KEY (account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_payment_record_channel
        CHECK (channel IN ('MOCK', 'WECHAT', 'ALIPAY')),
    CONSTRAINT chk_payment_record_status
        CHECK (status IN ('CREATED', 'PAYING', 'SUCCESS', 'FAILED', 'CLOSED')),
    CONSTRAINT chk_payment_record_amount
        CHECK (amount > 0),
    CONSTRAINT chk_payment_record_attempt
        CHECK (attempt_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='支付记录';

-- ---------------------------------------------------------------------------
-- 2. 退款记录（审批与执行分开；退款总额事务内校验不超过成功支付金额）
-- ---------------------------------------------------------------------------
CREATE TABLE refund_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no VARCHAR(32) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    payment_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    requested_by_account_id BIGINT UNSIGNED NOT NULL,
    reviewed_by_account_id BIGINT UNSIGNED NULL,
    reviewed_at DATETIME(3) NULL,
    refunded_at DATETIME(3) NULL,
    failure_reason VARCHAR(500) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_record_no (refund_no),
    UNIQUE KEY uk_refund_record_idempotency (payment_id, idempotency_key),
    KEY idx_refund_record_booking (booking_id, status, created_at),
    KEY idx_refund_record_payment (payment_id, status),
    CONSTRAINT fk_refund_record_payment
        FOREIGN KEY (payment_id) REFERENCES payment_record (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_refund_record_requester
        FOREIGN KEY (requested_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_refund_record_reviewer
        FOREIGN KEY (reviewed_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_refund_record_status
        CHECK (status IN (
            'REQUESTED',
            'APPROVED',
            'PROCESSING',
            'SUCCESS',
            'REJECTED',
            'FAILED',
            'CANCELED'
        )),
    CONSTRAINT chk_refund_record_amount
        CHECK (amount > 0),
    CONSTRAINT chk_refund_record_review
        CHECK (
            status IN ('REQUESTED', 'CANCELED')
            OR reviewed_by_account_id IS NOT NULL
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='退款记录';

-- ---------------------------------------------------------------------------
-- 3. 支付回调日志（只存哈希与业务号，不存敏感报文；channel+callback_id 幂等）
-- ---------------------------------------------------------------------------
CREATE TABLE payment_callback_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    channel VARCHAR(16) NOT NULL,
    callback_id VARCHAR(128) NOT NULL,
    payment_no VARCHAR(32) NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result VARCHAR(16) NOT NULL,
    error_message VARCHAR(500) NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_callback
        (channel, callback_id),
    KEY idx_payment_callback_payment
        (payment_no, received_at),
    CONSTRAINT chk_payment_callback_channel
        CHECK (channel IN ('MOCK', 'WECHAT', 'ALIPAY')),
    CONSTRAINT chk_payment_callback_result
        CHECK (result IN ('RECEIVED', 'SUCCESS', 'DUPLICATE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='支付回调日志';

-- ---------------------------------------------------------------------------
-- 4. 服务履约记录（uk booking_id：一单最多一条履约记录）
-- ---------------------------------------------------------------------------
CREATE TABLE service_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no VARCHAR(32) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    worker_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    departed_at DATETIME(3) NULL,
    check_in_at DATETIME(3) NULL,
    check_in_longitude DECIMAL(10, 7) NULL,
    check_in_latitude DECIMAL(10, 7) NULL,
    check_in_source VARCHAR(16) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    completion_requested_at DATETIME(3) NULL,
    confirmation_deadline DATETIME(3) NULL,
    customer_confirmed_at DATETIME(3) NULL,
    confirm_source VARCHAR(16) NULL,
    service_result VARCHAR(1000) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_record_no (record_no),
    UNIQUE KEY uk_service_record_booking (booking_id),
    KEY idx_service_record_worker
        (worker_id, status, created_at),
    KEY idx_service_record_confirm
        (status, confirmation_deadline),
    CONSTRAINT chk_service_record_status
        CHECK (status IN (
            'PENDING',
            'EN_ROUTE',
            'CHECKED_IN',
            'IN_SERVICE',
            'PENDING_CONFIRM',
            'COMPLETED',
            'EXCEPTION',
            'CANCELED'
        )),
    CONSTRAINT chk_service_record_checkin_source
        CHECK (check_in_source IS NULL OR check_in_source IN ('GPS', 'MANUAL', 'SYSTEM')),
    CONSTRAINT chk_service_record_confirm_source
        CHECK (confirm_source IS NULL OR confirm_source IN ('CUSTOMER', 'SYSTEM', 'OPERATOR')),
    CONSTRAINT chk_service_record_longitude
        CHECK (check_in_longitude IS NULL OR check_in_longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_service_record_latitude
        CHECK (check_in_latitude IS NULL OR check_in_latitude BETWEEN -90 AND 90)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务履约记录';

-- ---------------------------------------------------------------------------
-- 5. 服务检查项快照（来自下单时的服务标准快照，不依赖目录当前模板）
-- ---------------------------------------------------------------------------
CREATE TABLE service_checklist_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_record_id BIGINT UNSIGNED NOT NULL,
    source_standard_item_id BIGINT UNSIGNED NULL,
    standard_group VARCHAR(64) NULL,
    item_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    required TINYINT(1) NOT NULL DEFAULT 1,
    evidence_required TINYINT(1) NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    completed_at DATETIME(3) NULL,
    completed_by_account_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_checklist_item
        (service_record_id, sort_order),
    KEY idx_service_checklist_status
        (service_record_id, required, completed),
    CONSTRAINT fk_service_checklist_record
        FOREIGN KEY (service_record_id) REFERENCES service_record (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_checklist_completed_by
        FOREIGN KEY (completed_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_checklist_required
        CHECK (required IN (0, 1)),
    CONSTRAINT chk_service_checklist_evidence
        CHECK (evidence_required IN (0, 1)),
    CONSTRAINT chk_service_checklist_completed
        CHECK (completed IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务检查项快照';

-- ---------------------------------------------------------------------------
-- 6. 服务履约证据（uk file_id：一个文件只绑定一条证据）
-- ---------------------------------------------------------------------------
CREATE TABLE service_evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_record_id BIGINT UNSIGNED NOT NULL,
    checklist_item_id BIGINT UNSIGNED NULL,
    evidence_type VARCHAR(24) NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    description VARCHAR(500) NULL,
    uploaded_by_account_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_evidence_file (file_id),
    KEY idx_service_evidence_record
        (service_record_id, evidence_type, created_at),
    KEY idx_service_evidence_checklist
        (checklist_item_id),
    CONSTRAINT fk_service_evidence_record
        FOREIGN KEY (service_record_id) REFERENCES service_record (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_evidence_checklist
        FOREIGN KEY (checklist_item_id) REFERENCES service_checklist_item (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_evidence_uploader
        FOREIGN KEY (uploaded_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_evidence_type
        CHECK (evidence_type IN (
            'BEFORE_PHOTO',
            'AFTER_PHOTO',
            'CHECKLIST_PHOTO',
            'DOCUMENT',
            'OTHER'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务履约证据';

-- ---------------------------------------------------------------------------
-- 7. 履约异常（异常不改订单金额；现场加项走异常申请）
-- ---------------------------------------------------------------------------
CREATE TABLE service_exception (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exception_no VARCHAR(32) NOT NULL,
    service_record_id BIGINT UNSIGNED NOT NULL,
    exception_type VARCHAR(32) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    reported_by_account_id BIGINT UNSIGNED NOT NULL,
    handled_by_account_id BIGINT UNSIGNED NULL,
    resolution VARCHAR(1000) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reported_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_exception_no (exception_no),
    KEY idx_service_exception_record
        (service_record_id, status, reported_at),
    CONSTRAINT fk_service_exception_record
        FOREIGN KEY (service_record_id) REFERENCES service_record (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_exception_reporter
        FOREIGN KEY (reported_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_exception_handler
        FOREIGN KEY (handled_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_exception_type
        CHECK (exception_type IN (
            'CUSTOMER_ABSENT',
            'ADDRESS_INCORRECT',
            'ACCESS_DENIED',
            'SAFETY_RISK',
            'MATERIAL_MISSING',
            'EXTRA_SERVICE',
            'OTHER'
        )),
    CONSTRAINT chk_service_exception_status
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'REJECTED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='履约异常';

-- ---------------------------------------------------------------------------
-- 8. 评价维度字典（综合分 = 各维度加权平均）
-- ---------------------------------------------------------------------------
CREATE TABLE review_dimension (
    dimension_code VARCHAR(32) NOT NULL,
    dimension_name VARCHAR(64) NOT NULL,
    max_score DECIMAL(3,2) NOT NULL DEFAULT 5.00,
    weight DECIMAL(5,4) NOT NULL DEFAULT 0.2500,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (dimension_code),
    CONSTRAINT chk_review_dimension_score
        CHECK (max_score > 0),
    CONSTRAINT chk_review_dimension_weight
        CHECK (weight >= 0 AND weight <= 1),
    CONSTRAINT chk_review_dimension_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='评价维度';

-- ---------------------------------------------------------------------------
-- 9. 订单评价（uk booking_id：一单最多一条评价；隐藏保留原始记录）
-- ---------------------------------------------------------------------------
CREATE TABLE service_review (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_no VARCHAR(32) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    worker_id BIGINT UNSIGNED NOT NULL,
    overall_score DECIMAL(3,2) NOT NULL,
    content VARCHAR(2000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    hidden_reason VARCHAR(500) NULL,
    hidden_by_account_id BIGINT UNSIGNED NULL,
    hidden_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_review_no (review_no),
    UNIQUE KEY uk_service_review_booking (booking_id),
    KEY idx_service_review_worker
        (worker_id, status, created_at),
    KEY idx_service_review_score
        (worker_id, overall_score),
    CONSTRAINT fk_service_review_hidden_by
        FOREIGN KEY (hidden_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_review_score
        CHECK (overall_score BETWEEN 0 AND 5),
    CONSTRAINT chk_service_review_status
        CHECK (status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT chk_service_review_hidden
        CHECK (
            status = 'VISIBLE'
            OR hidden_reason IS NOT NULL
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='订单评价';

-- ---------------------------------------------------------------------------
-- 10. 评价维度分数
-- ---------------------------------------------------------------------------
CREATE TABLE review_dimension_score (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id BIGINT UNSIGNED NOT NULL,
    dimension_code VARCHAR(32) NOT NULL,
    score DECIMAL(3,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_dimension_score
        (review_id, dimension_code),
    KEY idx_review_dimension_code
        (dimension_code, score),
    CONSTRAINT fk_review_dimension_score_review
        FOREIGN KEY (review_id) REFERENCES service_review (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_review_dimension_score_dimension
        FOREIGN KEY (dimension_code) REFERENCES review_dimension (dimension_code)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_review_dimension_score_value
        CHECK (score BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='评价维度分数';

-- ---------------------------------------------------------------------------
-- 11. 评价附件（首版仅建表，图片上传走文件模块私有桶）
-- ---------------------------------------------------------------------------
CREATE TABLE review_evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_evidence_file (file_id),
    KEY idx_review_evidence_review
        (review_id, sort_order),
    CONSTRAINT fk_review_evidence_review
        FOREIGN KEY (review_id) REFERENCES service_review (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='评价附件';

-- ---------------------------------------------------------------------------
-- 12. 售后工单（结论与退款执行分离：工单只存 refund_id 引用）
-- ---------------------------------------------------------------------------
CREATE TABLE after_sale_ticket (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_no VARCHAR(32) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    service_record_id BIGINT UNSIGNED NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    worker_id BIGINT UNSIGNED NULL,
    ticket_type VARCHAR(32) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    handler_id BIGINT UNSIGNED NULL,
    due_at DATETIME(3) NULL,
    resolution_type VARCHAR(24) NULL,
    resolution_text VARCHAR(2000) NULL,
    responsibility VARCHAR(24) NOT NULL DEFAULT 'UNDETERMINED',
    refund_id BIGINT UNSIGNED NULL,
    rework_booking_id BIGINT UNSIGNED NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_ticket_no (ticket_no),
    KEY idx_after_sale_ticket_booking
        (booking_id, status),
    KEY idx_after_sale_ticket_handler
        (handler_id, status, due_at),
    KEY idx_after_sale_ticket_customer
        (customer_id, submitted_at),
    CONSTRAINT fk_after_sale_ticket_handler
        FOREIGN KEY (handler_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_after_sale_ticket_type
        CHECK (ticket_type IN (
            'QUALITY_COMPLAINT',
            'LATE_ARRIVAL',
            'DAMAGE',
            'REWORK',
            'REFUND_REQUEST',
            'SERVICE_ATTITUDE',
            'OTHER'
        )),
    CONSTRAINT chk_after_sale_ticket_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_after_sale_ticket_status
        CHECK (status IN (
            'SUBMITTED',
            'PROCESSING',
            'PENDING_CUSTOMER',
            'PENDING_WORKER',
            'PENDING_REVIEW',
            'RESOLVED',
            'REJECTED',
            'CLOSED'
        )),
    CONSTRAINT chk_after_sale_ticket_resolution
        CHECK (resolution_type IS NULL OR resolution_type IN (
            'REFUND',
            'PARTIAL_REFUND',
            'REWORK',
            'COMPENSATION',
            'EXPLANATION',
            'NO_ACTION'
        )),
    CONSTRAINT chk_after_sale_ticket_responsibility
        CHECK (responsibility IN (
            'UNDETERMINED',
            'COMPANY',
            'WORKER',
            'CUSTOMER',
            'SHARED',
            'NONE'
        )),
    CONSTRAINT chk_after_sale_ticket_resolved
        CHECK (
            status NOT IN ('RESOLVED', 'CLOSED')
            OR resolution_text IS NOT NULL
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='售后工单';

-- ---------------------------------------------------------------------------
-- 13. 售后工单时间线
-- ---------------------------------------------------------------------------
CREATE TABLE after_sale_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NULL,
    description VARCHAR(1000) NULL,
    operator_account_id BIGINT UNSIGNED NULL,
    operator_role VARCHAR(24) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_after_sale_event_ticket
        (ticket_id, created_at),
    CONSTRAINT fk_after_sale_event_ticket
        FOREIGN KEY (ticket_id) REFERENCES after_sale_ticket (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_after_sale_event_operator
        FOREIGN KEY (operator_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='售后工单时间线';

-- ---------------------------------------------------------------------------
-- 14. 售后附件（uk file_id：同一文件不能重复绑定）
-- ---------------------------------------------------------------------------
CREATE TABLE after_sale_attachment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    attachment_type VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER_EVIDENCE',
    uploaded_by_account_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_attachment_file (file_id),
    KEY idx_after_sale_attachment_ticket
        (ticket_id, created_at),
    CONSTRAINT fk_after_sale_attachment_ticket
        FOREIGN KEY (ticket_id) REFERENCES after_sale_ticket (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_after_sale_attachment_uploader
        FOREIGN KEY (uploaded_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_after_sale_attachment_type
        CHECK (attachment_type IN (
            'CUSTOMER_EVIDENCE',
            'HANDLER_EVIDENCE',
            'RESOLUTION_DOCUMENT',
            'OTHER'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='售后附件';

-- ---------------------------------------------------------------------------
-- 15. 人员质量汇总（供派单评分与运营查看，可重算，不作资金/订单事实来源）
-- ---------------------------------------------------------------------------
CREATE TABLE worker_quality_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT UNSIGNED NOT NULL,
    review_count INT UNSIGNED NOT NULL DEFAULT 0,
    average_rating DECIMAL(3,2) NOT NULL DEFAULT 5.00,
    completed_order_count INT UNSIGNED NOT NULL DEFAULT 0,
    complaint_count INT UNSIGNED NOT NULL DEFAULT 0,
    late_count INT UNSIGNED NOT NULL DEFAULT 0,
    rework_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_calculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_quality_stat_worker (worker_id),
    KEY idx_worker_quality_rating (average_rating),
    KEY idx_worker_quality_complaints (complaint_count),
    CONSTRAINT chk_worker_quality_rating
        CHECK (average_rating BETWEEN 0 AND 5),
    CONSTRAINT chk_worker_quality_counts
        CHECK (
            review_count >= 0
            AND completed_order_count >= 0
            AND complaint_count >= 0
            AND late_count >= 0
            AND rework_count >= 0
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='人员质量统计';

-- ---------------------------------------------------------------------------
-- 种子数据：评价维度（docs/modules/11 第 11.1 节初始化）
-- ---------------------------------------------------------------------------
INSERT INTO review_dimension
    (dimension_code, dimension_name, max_score, weight, sort_order)
VALUES
    ('SERVICE', '服务效果', 5.00, 0.2500, 10),
    ('ATTITUDE', '服务态度', 5.00, 0.2500, 20),
    ('QUALITY', '服务质量', 5.00, 0.2500, 30),
    ('PUNCTUALITY', '准时上门', 5.00, 0.2500, 40);
