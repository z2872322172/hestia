-- ============================================================================
-- V5 预约订单与派单（docs/modules/07、docs/modules/08）
-- booking_order / booking_item / booking_status_log /
-- booking_schedule_history / booking_cancellation
-- dispatch_policy / dispatch_job / dispatch_candidate / order_assignment
--
-- 跨模块约定（docs/modules/07 第 15.1 节）：
--   * service_item_id / service_area_id / worker_slot_id / current_worker_id
--     均为逻辑外键，不建跨模块级联；
--   * 指向 account / customer_profile 的外键（V1/V2 已建）保留物理外键；
--   * 订单修改统一使用 version 乐观锁，状态流转使用条件更新。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 预约订单主表
-- ---------------------------------------------------------------------------
CREATE TABLE booking_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    customer_no_snapshot VARCHAR(32) NOT NULL,
    service_item_id BIGINT UNSIGNED NOT NULL,
    service_item_version BIGINT UNSIGNED NOT NULL,
    service_item_no_snapshot VARCHAR(32) NOT NULL,
    service_name_snapshot VARCHAR(128) NOT NULL,
    service_area_id BIGINT UNSIGNED NOT NULL,
    service_area_name_snapshot VARCHAR(128) NULL,
    address_id BIGINT UNSIGNED NOT NULL,
    address_version BIGINT UNSIGNED NOT NULL,
    address_snapshot_json JSON NOT NULL,
    contact_name_snapshot VARCHAR(64) NOT NULL,
    contact_phone_snapshot VARCHAR(32) NOT NULL,
    appointment_date DATE NOT NULL,
    service_window_id BIGINT UNSIGNED NOT NULL,
    service_window_name_snapshot VARCHAR(64) NOT NULL,
    appointment_start DATETIME(3) NOT NULL,
    appointment_end DATETIME(3) NOT NULL,
    worker_slot_id BIGINT UNSIGNED NOT NULL,
    current_worker_id BIGINT UNSIGNED NULL,
    order_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status VARCHAR(24) NOT NULL DEFAULT 'UNPAID',
    assignment_status VARCHAR(32) NOT NULL DEFAULT 'WAITING_PAYMENT',
    fulfillment_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    after_sale_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    review_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REVIEWED',
    service_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    addon_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    material_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    travel_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payable_amount DECIMAL(12,2) NOT NULL,
    paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    price_snapshot_json JSON NOT NULL,
    service_standard_snapshot_json JSON NOT NULL,
    cancellation_policy_snapshot_json JSON NOT NULL,
    payment_deadline DATETIME(3) NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
    cancel_reason_code VARCHAR(32) NULL,
    cancel_reason_text VARCHAR(500) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    paid_at DATETIME(3) NULL,
    assignment_accepted_at DATETIME(3) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    canceled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_order_no (order_no),
    KEY idx_booking_order_slot (worker_slot_id),
    KEY idx_booking_order_customer
        (customer_id, created_at),
    KEY idx_booking_order_worker
        (current_worker_id, appointment_start),
    KEY idx_booking_order_status
        (order_status, payment_deadline, appointment_start),
    KEY idx_booking_order_area
        (service_area_id, appointment_date, order_status),
    CONSTRAINT fk_booking_order_customer
        FOREIGN KEY (customer_id) REFERENCES customer_profile (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_booking_order_creator
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_booking_order_status
        CHECK (order_status IN (
            'PENDING_PAYMENT',
            'CONFIRMED',
            'EN_ROUTE',
            'IN_SERVICE',
            'PENDING_CONFIRM',
            'COMPLETED',
            'CANCELED'
        )),
    CONSTRAINT chk_booking_order_payment
        CHECK (payment_status IN (
            'UNPAID',
            'PAID',
            'REFUNDING',
            'PARTIALLY_REFUNDED',
            'REFUNDED'
        )),
    CONSTRAINT chk_booking_order_assignment
        CHECK (assignment_status IN (
            'WAITING_PAYMENT',
            'PENDING_ACCEPT',
            'ACCEPTED',
            'REASSIGNING',
            'MANUAL_REQUIRED',
            'CANCELED'
        )),
    CONSTRAINT chk_booking_order_fulfillment
        CHECK (fulfillment_status IN (
            'NOT_STARTED',
            'EN_ROUTE',
            'CHECKED_IN',
            'IN_SERVICE',
            'PENDING_CONFIRM',
            'COMPLETED',
            'EXCEPTION'
        )),
    CONSTRAINT chk_booking_order_after_sale
        CHECK (after_sale_status IN (
            'NONE',
            'PROCESSING',
            'RESOLVED',
            'CLOSED'
        )),
    CONSTRAINT chk_booking_order_review
        CHECK (review_status IN ('NOT_REVIEWED', 'REVIEWED')),
    CONSTRAINT chk_booking_order_source
        CHECK (source IN ('CUSTOMER', 'OPERATOR', 'SYSTEM')),
    CONSTRAINT chk_booking_order_amount
        CHECK (
            service_amount >= 0
            AND addon_amount >= 0
            AND material_amount >= 0
            AND travel_fee >= 0
            AND discount_amount >= 0
            AND payable_amount >= 0
            AND paid_amount >= 0
            AND refunded_amount >= 0
            AND refunded_amount <= paid_amount
        ),
    CONSTRAINT chk_booking_order_time
        CHECK (appointment_end > appointment_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '预约订单';

-- ---------------------------------------------------------------------------
-- 2. 订单金额明细（BASE/ADDON/MATERIAL/TRAVEL/DISCOUNT 各一行）
-- ---------------------------------------------------------------------------
CREATE TABLE booking_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    item_code VARCHAR(32) NULL,
    item_name_snapshot VARCHAR(128) NOT NULL,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL DEFAULT 1.00,
    amount DECIMAL(12,2) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_booking_item_booking
        (booking_id, sort_order),
    CONSTRAINT fk_booking_item_order
        FOREIGN KEY (booking_id) REFERENCES booking_order (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_booking_item_type
        CHECK (item_type IN (
            'BASE',
            'ADDON',
            'MATERIAL',
            'TRAVEL',
            'DISCOUNT',
            'ADJUSTMENT'
        )),
    CONSTRAINT chk_booking_item_amount
        CHECK (quantity > 0 AND unit_price_snapshot >= 0 AND amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单金额明细';

-- ---------------------------------------------------------------------------
-- 3. 订单主状态日志（只追加，与状态变化同事务写入）
-- ---------------------------------------------------------------------------
CREATE TABLE booking_status_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NOT NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_role VARCHAR(24) NULL,
    operation_source VARCHAR(24) NOT NULL DEFAULT 'USER',
    reason_code VARCHAR(32) NULL,
    reason_text VARCHAR(500) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_booking_status_log_booking
        (booking_id, created_at),
    CONSTRAINT fk_booking_status_log_order
        FOREIGN KEY (booking_id) REFERENCES booking_order (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_booking_status_log_operator
        FOREIGN KEY (operator_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_booking_status_log_source
        CHECK (operation_source IN ('USER', 'WORKER', 'OPERATOR', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单主状态日志';

-- ---------------------------------------------------------------------------
-- 4. 订单改期历史
-- ---------------------------------------------------------------------------
CREATE TABLE booking_schedule_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    old_appointment_date DATE NOT NULL,
    old_service_window_id BIGINT UNSIGNED NOT NULL,
    old_worker_slot_id BIGINT UNSIGNED NOT NULL,
    old_worker_id BIGINT UNSIGNED NULL,
    new_appointment_date DATE NOT NULL,
    new_service_window_id BIGINT UNSIGNED NOT NULL,
    new_worker_slot_id BIGINT UNSIGNED NOT NULL,
    new_worker_id BIGINT UNSIGNED NULL,
    reason_code VARCHAR(32) NULL,
    reason_text VARCHAR(500) NULL,
    operator_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_booking_schedule_history_order
        (booking_id, created_at),
    CONSTRAINT fk_booking_schedule_history_order
        FOREIGN KEY (booking_id) REFERENCES booking_order (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_booking_schedule_history_operator
        FOREIGN KEY (operator_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单改期历史';

-- ---------------------------------------------------------------------------
-- 5. 订单取消记录（uk booking_id：每单最多取消一次）
-- ---------------------------------------------------------------------------
CREATE TABLE booking_cancellation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(500) NULL,
    canceled_by_account_id BIGINT UNSIGNED NULL,
    canceled_by_role VARCHAR(24) NULL,
    fee_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    refundable_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    policy_snapshot_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_cancellation_order (booking_id),
    CONSTRAINT fk_booking_cancellation_order
        FOREIGN KEY (booking_id) REFERENCES booking_order (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_booking_cancellation_operator
        FOREIGN KEY (canceled_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_booking_cancellation_amount
        CHECK (fee_amount >= 0 AND refundable_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单取消和费用快照';

-- ---------------------------------------------------------------------------
-- 6. 派单评分策略（同一时刻最多一个 ACTIVE，由应用层切换）
-- ---------------------------------------------------------------------------
CREATE TABLE dispatch_policy (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_no VARCHAR(32) NOT NULL,
    policy_name VARCHAR(64) NOT NULL,
    weights_json JSON NOT NULL,
    max_candidates INT NOT NULL DEFAULT 5,
    accept_timeout_minutes INT NOT NULL DEFAULT 10,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispatch_policy_no (policy_no),
    KEY idx_dispatch_policy_status (status),
    CONSTRAINT fk_dispatch_policy_creator
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_dispatch_policy_candidates
        CHECK (max_candidates BETWEEN 1 AND 20),
    CONSTRAINT chk_dispatch_policy_timeout
        CHECK (accept_timeout_minutes BETWEEN 1 AND 120),
    CONSTRAINT chk_dispatch_policy_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '派单评分策略';

-- ---------------------------------------------------------------------------
-- 7. 派单任务（uk booking_id：一单一个派单任务）
-- ---------------------------------------------------------------------------
CREATE TABLE dispatch_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_no VARCHAR(32) NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    policy_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING_PAYMENT',
    current_assignment_id BIGINT UNSIGNED NULL,
    candidate_count INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    manual_required_reason VARCHAR(500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_assigned_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispatch_job_no (job_no),
    UNIQUE KEY uk_dispatch_job_booking (booking_id),
    KEY idx_dispatch_job_status (status, updated_at),
    KEY idx_dispatch_job_policy (policy_id),
    CONSTRAINT fk_dispatch_job_policy
        FOREIGN KEY (policy_id) REFERENCES dispatch_policy (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_dispatch_job_status
        CHECK (status IN (
            'WAITING_PAYMENT',
            'READY',
            'PENDING_ACCEPT',
            'ACCEPTED',
            'REASSIGNING',
            'MANUAL_REQUIRED',
            'CANCELED'
        )),
    CONSTRAINT chk_dispatch_job_count
        CHECK (candidate_count >= 0 AND attempt_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单派单任务';

-- ---------------------------------------------------------------------------
-- 8. 派单候选人员快照（同任务同人员唯一）
-- ---------------------------------------------------------------------------
CREATE TABLE dispatch_candidate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dispatch_job_id BIGINT UNSIGNED NOT NULL,
    policy_id BIGINT UNSIGNED NOT NULL,
    worker_id BIGINT UNSIGNED NOT NULL,
    rank_no INT NOT NULL,
    total_score DECIMAL(6,3) NOT NULL,
    score_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'GENERATED',
    generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispatch_candidate
        (dispatch_job_id, worker_id),
    KEY idx_dispatch_candidate_rank
        (dispatch_job_id, status, rank_no),
    KEY idx_dispatch_candidate_worker
        (worker_id, generated_at),
    CONSTRAINT fk_dispatch_candidate_job
        FOREIGN KEY (dispatch_job_id) REFERENCES dispatch_job (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_dispatch_candidate_policy
        FOREIGN KEY (policy_id) REFERENCES dispatch_policy (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_dispatch_candidate_rank
        CHECK (rank_no > 0),
    CONSTRAINT chk_dispatch_candidate_score
        CHECK (total_score BETWEEN 0 AND 100),
    CONSTRAINT chk_dispatch_candidate_status
        CHECK (status IN ('GENERATED', 'SELECTED', 'SKIPPED', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '派单候选人员快照';

-- ---------------------------------------------------------------------------
-- 9. 订单人员分配记录（replaced_assignment_id 自引用：改派链）
-- ---------------------------------------------------------------------------
CREATE TABLE order_assignment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    assignment_no VARCHAR(32) NOT NULL,
    dispatch_job_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    worker_id BIGINT UNSIGNED NOT NULL,
    assign_type VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING_PAYMENT',
    assigned_by_account_id BIGINT UNSIGNED NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    accept_deadline DATETIME(3) NULL,
    accepted_at DATETIME(3) NULL,
    rejected_at DATETIME(3) NULL,
    reject_reason_code VARCHAR(32) NULL,
    reject_reason_text VARCHAR(500) NULL,
    replaced_assignment_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_assignment_no (assignment_no),
    KEY idx_order_assignment_job
        (dispatch_job_id, status, assigned_at),
    KEY idx_order_assignment_worker
        (worker_id, status, accept_deadline),
    KEY idx_order_assignment_booking
        (booking_id, assigned_at),
    CONSTRAINT fk_order_assignment_job
        FOREIGN KEY (dispatch_job_id) REFERENCES dispatch_job (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_assignment_assigner
        FOREIGN KEY (assigned_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_assignment_replaced
        FOREIGN KEY (replaced_assignment_id) REFERENCES order_assignment (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_order_assignment_type
        CHECK (assign_type IN ('AUTO', 'MANUAL', 'REASSIGN')),
    CONSTRAINT chk_order_assignment_status
        CHECK (status IN (
            'WAITING_PAYMENT',
            'PENDING_ACCEPT',
            'ACCEPTED',
            'REJECTED',
            'EXPIRED',
            'REASSIGNED',
            'CANCELED'
        ))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单人员分配记录';

-- ---------------------------------------------------------------------------
-- 种子数据：默认 ACTIVE 派单策略（docs/modules/08 第 7 节默认权重）
-- ---------------------------------------------------------------------------
INSERT INTO dispatch_policy
    (policy_no, policy_name, weights_json, max_candidates, accept_timeout_minutes, status)
VALUES
    ('POL_DEFAULT', '默认派单策略',
     '{"area_score":25,"workload_score":20,"quality_score":20,"skill_score":15,"continuity_score":10,"preference_score":10}',
     5, 10, 'ACTIVE');
