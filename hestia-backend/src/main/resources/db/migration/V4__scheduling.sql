-- ============================================================================
-- V4 排班与可用时段（docs/modules/06）
-- service_window：标准服务时间窗（公司统一配置）
-- worker_schedule：人员排班与请假
-- worker_slot：人员时段库存（可锁定资源）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 标准服务时间窗
-- ---------------------------------------------------------------------------
CREATE TABLE service_window (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    window_code VARCHAR(32) NOT NULL COMMENT '时间窗编码，例如 MORNING',
    window_name VARCHAR(64) NOT NULL COMMENT '展示名称，例如 上午',
    start_time TIME NOT NULL COMMENT '开始时间',
    end_time TIME NOT NULL COMMENT '结束时间，必须晚于开始时间',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建人账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改人账号 ID',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_window_code (window_code),
    KEY idx_service_window_order (status, sort_order),
    CONSTRAINT chk_service_window_time
        CHECK (end_time > start_time),
    CONSTRAINT chk_service_window_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '标准服务时间窗';

-- ---------------------------------------------------------------------------
-- 2. 人员排班
-- ---------------------------------------------------------------------------
CREATE TABLE worker_schedule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT UNSIGNED NOT NULL COMMENT '人员 ID（worker_profile.id）',
    service_date DATE NOT NULL COMMENT '服务日期',
    window_id BIGINT UNSIGNED NOT NULL COMMENT '时间窗 ID',
    schedule_type VARCHAR(16) NOT NULL DEFAULT 'WORK' COMMENT 'WORK/LEAVE/TRAINING/REST',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELED',
    source VARCHAR(16) NOT NULL DEFAULT 'OPERATOR' COMMENT '来源：WORKER/OPERATOR/SYSTEM',
    note VARCHAR(500) NULL COMMENT '备注（请假原因等）',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建人账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改人账号 ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_schedule (worker_id, service_date, window_id),
    KEY idx_worker_schedule_date (service_date, window_id, status),
    KEY idx_worker_schedule_worker (worker_id, service_date, status),
    CONSTRAINT fk_worker_schedule_worker
        FOREIGN KEY (worker_id) REFERENCES worker_profile (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_schedule_window
        FOREIGN KEY (window_id) REFERENCES service_window (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_schedule_creator
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_worker_schedule_type
        CHECK (schedule_type IN ('WORK', 'LEAVE', 'TRAINING', 'REST')),
    CONSTRAINT chk_worker_schedule_status
        CHECK (status IN ('ACTIVE', 'CANCELED')),
    CONSTRAINT chk_worker_schedule_source
        CHECK (source IN ('WORKER', 'OPERATOR', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '人员排班';

-- ---------------------------------------------------------------------------
-- 3. 人员时段库存
-- ---------------------------------------------------------------------------

-- booking_id 是订单模块的业务主键，只用逻辑外键：时段与订单互相不建级联，
-- 避免 V5 迁移出现循环依赖（docs/modules/06 第 13 节）。
-- chk_worker_slot_order_binding 用数据库约束兜底状态机的绑定不变量：
-- AVAILABLE 必须无订单绑定，HELD 必须绑定订单且带锁定到期时间，BOOKED 绑定订单且无到期时间。
CREATE TABLE worker_slot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT UNSIGNED NOT NULL COMMENT '人员 ID（worker_profile.id）',
    schedule_id BIGINT UNSIGNED NOT NULL COMMENT '生成该时段的排班 ID',
    service_date DATE NOT NULL COMMENT '服务日期',
    window_id BIGINT UNSIGNED NOT NULL COMMENT '时间窗 ID',
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE'
        COMMENT 'AVAILABLE/HELD/BOOKED/BLOCKED/CLOSED',
    booking_id BIGINT UNSIGNED NULL COMMENT '占用该时段的订单 ID（booking_order.id）',
    hold_expires_at DATETIME(3) NULL COMMENT '锁定到期时间，超时未支付自动释放',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建人账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改人账号 ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_slot_time (worker_id, service_date, window_id),
    KEY idx_worker_slot_availability (service_date, window_id, status),
    KEY idx_worker_slot_worker_date (worker_id, service_date, status),
    KEY idx_worker_slot_booking (booking_id),
    CONSTRAINT fk_worker_slot_worker
        FOREIGN KEY (worker_id) REFERENCES worker_profile (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_slot_schedule
        FOREIGN KEY (schedule_id) REFERENCES worker_schedule (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_slot_window
        FOREIGN KEY (window_id) REFERENCES service_window (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_worker_slot_status
        CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED', 'BLOCKED', 'CLOSED')),
    CONSTRAINT chk_worker_slot_order_binding
        CHECK (
            (status = 'AVAILABLE' AND booking_id IS NULL AND hold_expires_at IS NULL)
            OR (status = 'HELD' AND booking_id IS NOT NULL AND hold_expires_at IS NOT NULL)
            OR (status = 'BOOKED' AND booking_id IS NOT NULL AND hold_expires_at IS NULL)
            OR (status IN ('BLOCKED', 'CLOSED'))
        )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '人员时段库存';

-- ---------------------------------------------------------------------------
-- 种子数据：docs/modules/06 第 5 节的三个标准时间窗
-- ---------------------------------------------------------------------------
INSERT INTO service_window (window_code, window_name, start_time, end_time, sort_order, status)
VALUES
    ('MORNING',   '上午', '08:00:00', '12:00:00', 10, 'ACTIVE'),
    ('AFTERNOON', '下午', '13:00:00', '17:00:00', 20, 'ACTIVE'),
    ('EVENING',   '晚上', '18:00:00', '21:00:00', 30, 'ACTIVE');
