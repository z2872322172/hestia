-- ============================================================================
-- V3 服务目录与人员（docs/07 第 10.3 节）
--
-- 建表顺序严格按依赖：分类 -> 服务项目 -> 规格组 -> 规格值 -> 价格阶梯 -> 标准检查项
--                     人员档案 -> 技能 -> 证书 -> 服务区域 -> 资质要求
--
-- 跨模块约定：worker_skill.service_item_id 是"目录模块的业务编号引用"，
-- 刻意不建外键。建了外键就等于把两个模块的表绑死，之后想拆服务或换存储都动不了。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 服务分类（多级树，业务层限制最多三层）
-- ---------------------------------------------------------------------------
CREATE TABLE service_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_no VARCHAR(32) NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    category_name VARCHAR(64) NOT NULL,
    icon_url VARCHAR(512) NULL,
    description VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_category_no (category_no),
    KEY idx_service_category_parent (parent_id, sort_order, status),
    CONSTRAINT fk_service_category_parent
        FOREIGN KEY (parent_id) REFERENCES service_category (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_category_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务分类';

-- ---------------------------------------------------------------------------
-- 2. 服务项目
-- ---------------------------------------------------------------------------
CREATE TABLE service_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_no VARCHAR(32) NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    short_description VARCHAR(255) NULL,
    detail_description TEXT NULL,
    cover_url VARCHAR(512) NULL,
    service_unit VARCHAR(24) NOT NULL DEFAULT 'ORDER',
    pricing_mode VARCHAR(24) NOT NULL DEFAULT 'FIXED',
    base_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    duration_minutes INT NOT NULL DEFAULT 240,
    min_quantity DECIMAL(10,2) NOT NULL DEFAULT 1.00,
    max_quantity DECIMAL(10,2) NULL,
    service_scope TEXT NULL,
    service_standard_summary TEXT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    offline_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_item_no (item_no),
    KEY idx_service_item_category (category_id, status),
    KEY idx_service_item_status (status, published_at),
    CONSTRAINT fk_service_item_category
        FOREIGN KEY (category_id) REFERENCES service_category (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_item_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_item_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_item_unit
        CHECK (service_unit IN ('ORDER', 'HOUR', 'SQUARE_METER', 'ITEM')),
    CONSTRAINT chk_service_item_pricing
        CHECK (pricing_mode IN (
            'FIXED',
            'AREA_TIER',
            'DURATION_TIER',
            'QUANTITY_TIER'
        )),
    CONSTRAINT chk_service_item_quantity
        CHECK (
            min_quantity > 0
            AND (max_quantity IS NULL OR max_quantity >= min_quantity)
        ),
    CONSTRAINT chk_service_item_price
        CHECK (base_price >= 0),
    CONSTRAINT chk_service_item_duration
        CHECK (duration_minutes > 0),
    CONSTRAINT chk_service_item_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务项目';

-- ---------------------------------------------------------------------------
-- 3. 服务规格组
-- ---------------------------------------------------------------------------
CREATE TABLE service_item_option (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_item_id BIGINT UNSIGNED NOT NULL,
    option_code VARCHAR(32) NOT NULL,
    option_name VARCHAR(64) NOT NULL,
    selection_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    required TINYINT(1) NOT NULL DEFAULT 0,
    min_select INT NOT NULL DEFAULT 0,
    max_select INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_item_option_code
        (service_item_id, option_code),
    KEY idx_service_item_option_order
        (service_item_id, sort_order, status),
    CONSTRAINT fk_service_item_option_item
        FOREIGN KEY (service_item_id) REFERENCES service_item (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_item_option_type
        CHECK (selection_type IN ('SINGLE', 'MULTIPLE')),
    CONSTRAINT chk_service_item_option_required
        CHECK (required IN (0, 1)),
    CONSTRAINT chk_service_item_option_select
        CHECK (
            min_select >= 0
            AND max_select >= 1
            AND max_select >= min_select
        ),
    CONSTRAINT chk_service_item_option_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务规格组';

-- ---------------------------------------------------------------------------
-- 4. 服务规格值（加项价格与时长增量）
-- ---------------------------------------------------------------------------
CREATE TABLE service_item_option_value (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    option_id BIGINT UNSIGNED NOT NULL,
    value_code VARCHAR(32) NOT NULL,
    value_name VARCHAR(64) NOT NULL,
    numeric_value DECIMAL(10,2) NULL,
    extra_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    duration_delta INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_item_option_value_code
        (option_id, value_code),
    KEY idx_service_item_option_value_order
        (option_id, sort_order, status),
    CONSTRAINT fk_service_item_option_value_option
        FOREIGN KEY (option_id) REFERENCES service_item_option (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_item_option_value_price
        CHECK (extra_price >= 0),
    CONSTRAINT chk_service_item_option_value_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务规格值和加项价格';

-- ---------------------------------------------------------------------------
-- 5. 阶梯价格（同一服务项目同一 tier_type 下区间不得重叠，业务层在发布时校验）
-- ---------------------------------------------------------------------------
CREATE TABLE service_price_tier (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_item_id BIGINT UNSIGNED NOT NULL,
    tier_type VARCHAR(24) NOT NULL,
    min_value DECIMAL(10,2) NOT NULL,
    max_value DECIMAL(10,2) NULL,
    fixed_price DECIMAL(12,2) NULL,
    unit_price DECIMAL(12,2) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_service_price_tier_item
        (service_item_id, tier_type, status, sort_order),
    CONSTRAINT fk_service_price_tier_item
        FOREIGN KEY (service_item_id) REFERENCES service_item (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_price_tier_type
        CHECK (tier_type IN ('AREA_TIER', 'DURATION_TIER', 'QUANTITY_TIER')),
    CONSTRAINT chk_service_price_tier_range
        CHECK (
            min_value >= 0
            AND (max_value IS NULL OR max_value > min_value)
        ),
    CONSTRAINT chk_service_price_tier_price
        CHECK (
            (fixed_price IS NOT NULL AND fixed_price >= 0)
            OR (unit_price IS NOT NULL AND unit_price >= 0)
        ),
    CONSTRAINT chk_service_price_tier_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务阶梯价格';

-- ---------------------------------------------------------------------------
-- 6. 服务标准检查项（履约模块复制快照，不直接读本表）
-- ---------------------------------------------------------------------------
CREATE TABLE service_standard_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_item_id BIGINT UNSIGNED NOT NULL,
    standard_group VARCHAR(64) NULL,
    item_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    required TINYINT(1) NOT NULL DEFAULT 1,
    evidence_required TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_service_standard_item
        (service_item_id, status, sort_order),
    CONSTRAINT fk_service_standard_item_item
        FOREIGN KEY (service_item_id) REFERENCES service_item (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_standard_item_required
        CHECK (required IN (0, 1)),
    CONSTRAINT chk_service_standard_item_evidence
        CHECK (evidence_required IN (0, 1)),
    CONSTRAINT chk_service_standard_item_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务标准检查项';

-- ---------------------------------------------------------------------------
-- 7. 人员档案
-- ---------------------------------------------------------------------------
CREATE TABLE worker_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_no VARCHAR(32) NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    gender VARCHAR(16) NULL,
    employment_type VARCHAR(16) NOT NULL,
    work_status VARCHAR(16) NOT NULL DEFAULT 'ONBOARDING',
    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    experience_years DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    introduction VARCHAR(1000) NULL,
    max_daily_orders INT NOT NULL DEFAULT 4,
    rating DECIMAL(3,2) NOT NULL DEFAULT 5.00,
    completed_order_count INT UNSIGNED NOT NULL DEFAULT 0,
    joined_at DATETIME(3) NULL,
    left_at DATETIME(3) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_profile_no (worker_no),
    UNIQUE KEY uk_worker_profile_account (account_id),
    KEY idx_worker_profile_status (work_status, audit_status),
    KEY idx_worker_profile_employment (employment_type, work_status),
    CONSTRAINT fk_worker_profile_account
        FOREIGN KEY (account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_worker_profile_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_worker_profile_employment
        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME')),
    CONSTRAINT chk_worker_profile_work_status
        CHECK (work_status IN ('ONBOARDING', 'ACTIVE', 'SUSPENDED', 'LEFT')),
    CONSTRAINT chk_worker_profile_audit
        CHECK (audit_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_worker_profile_experience
        CHECK (experience_years >= 0),
    CONSTRAINT chk_worker_profile_max_daily
        CHECK (max_daily_orders BETWEEN 1 AND 20),
    CONSTRAINT chk_worker_profile_rating
        CHECK (rating BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务人员业务档案';

-- ---------------------------------------------------------------------------
-- 8. 人员技能（service_item_id 是跨模块引用，不建外键）
-- ---------------------------------------------------------------------------
CREATE TABLE worker_skill (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT UNSIGNED NOT NULL,
    service_item_id BIGINT UNSIGNED NOT NULL,
    skill_level VARCHAR(16) NOT NULL DEFAULT 'JUNIOR',
    verified TINYINT(1) NOT NULL DEFAULT 0,
    verified_by_account_id BIGINT UNSIGNED NULL,
    verified_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_skill (worker_id, service_item_id),
    KEY idx_worker_skill_item (service_item_id, status, verified),
    CONSTRAINT fk_worker_skill_worker
        FOREIGN KEY (worker_id) REFERENCES worker_profile (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_skill_verifier
        FOREIGN KEY (verified_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_worker_skill_level
        CHECK (skill_level IN ('JUNIOR', 'INTERMEDIATE', 'SENIOR')),
    CONSTRAINT chk_worker_skill_verified
        CHECK (verified IN (0, 1)),
    CONSTRAINT chk_worker_skill_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='人员技能和服务项目授权';

-- ---------------------------------------------------------------------------
-- 9. 人员证书（编号密文 + HMAC 摘要，摘要用于重复校验）
-- ---------------------------------------------------------------------------
CREATE TABLE worker_certificate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT UNSIGNED NOT NULL,
    certificate_type VARCHAR(32) NOT NULL,
    certificate_no_cipher VARBINARY(512) NULL,
    certificate_no_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    file_id BIGINT UNSIGNED NULL,
    issued_at DATE NULL,
    expires_at DATE NULL,
    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    valid_status VARCHAR(16) NOT NULL DEFAULT 'VALID',
    verified_by_account_id BIGINT UNSIGNED NULL,
    verified_at DATETIME(3) NULL,
    remark VARCHAR(500) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_certificate_no
        (worker_id, certificate_type, certificate_no_hash),
    KEY idx_worker_certificate_expiry
        (valid_status, expires_at),
    KEY idx_worker_certificate_worker
        (worker_id, audit_status, valid_status),
    CONSTRAINT fk_worker_certificate_worker
        FOREIGN KEY (worker_id) REFERENCES worker_profile (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_certificate_verifier
        FOREIGN KEY (verified_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_worker_certificate_type
        CHECK (certificate_type IN (
            'IDENTITY',
            'HEALTH',
            'BACKGROUND_CHECK',
            'SKILL_CERTIFICATE',
            'SPECIAL_EQUIPMENT',
            'OTHER'
        )),
    CONSTRAINT chk_worker_certificate_audit
        CHECK (audit_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_worker_certificate_valid
        CHECK (valid_status IN ('VALID', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_worker_certificate_date
        CHECK (expires_at IS NULL OR issued_at IS NULL OR expires_at >= issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='人员证书和健康证明';

-- ---------------------------------------------------------------------------
-- 10. 人员服务区域
-- ---------------------------------------------------------------------------
CREATE TABLE worker_service_area (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id BIGINT UNSIGNED NOT NULL,
    service_area_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_service_area (worker_id, service_area_id),
    KEY idx_worker_service_area_area (service_area_id, status),
    CONSTRAINT fk_worker_service_area_worker
        FOREIGN KEY (worker_id) REFERENCES worker_profile (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_worker_service_area_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_worker_service_area_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='人员服务区域';

-- ---------------------------------------------------------------------------
-- 11. 服务项目资质要求
-- ---------------------------------------------------------------------------
CREATE TABLE service_qualification_requirement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_item_id BIGINT UNSIGNED NOT NULL,
    certificate_type VARCHAR(32) NOT NULL,
    required TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_account_id BIGINT UNSIGNED NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_qualification_requirement
        (service_item_id, certificate_type),
    KEY idx_service_qualification_service
        (service_item_id, status),
    CONSTRAINT chk_service_qualification_certificate
        CHECK (certificate_type IN (
            'IDENTITY',
            'HEALTH',
            'BACKGROUND_CHECK',
            'SKILL_CERTIFICATE',
            'SPECIAL_EQUIPMENT',
            'OTHER'
        )),
    CONSTRAINT chk_service_qualification_required
        CHECK (required IN (0, 1)),
    CONSTRAINT chk_service_qualification_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务项目证书要求';

-- ============================================================================
-- 种子数据
--
-- 只种"能跑通价格预览"的最小集合：两层分类 + 一个固定价项目 + 一个面积阶梯价项目。
-- 不种 DRAFT 项目：草稿的价值在于让运营自己建，预置草稿只会让冒烟测试分不清
-- "查询没生效"还是"种子数据本来就该看不见"。
-- ============================================================================

INSERT INTO service_category (category_no, parent_id, category_name, description, sort_order, status)
VALUES ('CAT202610230001', NULL, '居家保洁', '入户清洁类服务', 1, 'ACTIVE');

INSERT INTO service_category (category_no, parent_id, category_name, description, sort_order, status)
SELECT 'CAT202610230002', id, '日常保洁', '日常维持性清洁', 1, 'ACTIVE'
FROM service_category WHERE category_no = 'CAT202610230001';

INSERT INTO service_category (category_no, parent_id, category_name, description, sort_order, status)
SELECT 'CAT202610230003', id, '深度保洁', '按面积计价的深度清洁', 2, 'ACTIVE'
FROM service_category WHERE category_no = 'CAT202610230001';

INSERT INTO service_category (category_no, parent_id, category_name, description, sort_order, status)
SELECT 'CAT202610230004', id, '开荒保洁', '装修后首次清洁', 3, 'ACTIVE'
FROM service_category WHERE category_no = 'CAT202610230001';

-- 固定价项目：两居室日常保洁
INSERT INTO service_item (item_no, category_id, item_name, short_description, service_unit,
                          pricing_mode, base_price, duration_minutes, min_quantity, max_quantity,
                          service_scope, service_standard_summary, version, status, published_at)
SELECT 'SVC202610230001', c.id, '日常保洁·两居室', '80 平米以内两居室日常保洁，4 小时',
       'ORDER', 'FIXED', 199.00, 240, 1.00, 1.00,
       '不含高空外墙、家电内部拆洗', '6 项检查，服务前后各需拍照', 0, 'PUBLISHED', CURRENT_TIMESTAMP(3)
FROM service_category c WHERE c.category_no = 'CAT202610230002';

-- 面积阶梯价项目：深度保洁
INSERT INTO service_item (item_no, category_id, item_name, short_description, service_unit,
                          pricing_mode, base_price, duration_minutes, min_quantity, max_quantity,
                          service_scope, service_standard_summary, version, status, published_at)
SELECT 'SVC202610230002', c.id, '深度保洁·按面积', '按建筑面积分档计价，含厨卫深度清洁',
       'SQUARE_METER', 'AREA_TIER', 0.00, 300, 1.00, 1.00,
       '不含开荒、不含高空外墙', '按面积档位确定工时', 0, 'PUBLISHED', CURRENT_TIMESTAMP(3)
FROM service_category c WHERE c.category_no = 'CAT202610230003';

-- 规格组：日常保洁的加项
INSERT INTO service_item_option (service_item_id, option_code, option_name, selection_type,
                                 required, min_select, max_select, sort_order, status)
SELECT i.id, 'ADDON', '增值服务', 'MULTIPLE', 0, 0, 5, 1, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

INSERT INTO service_item_option_value (option_id, value_code, value_name, extra_price,
                                       duration_delta, sort_order, status)
SELECT o.id, 'FRIDGE', '冰箱内部清洗', 80.00, 30, 1, 'ACTIVE'
FROM service_item_option o
JOIN service_item i ON i.id = o.service_item_id
WHERE i.item_no = 'SVC202610230001' AND o.option_code = 'ADDON';

INSERT INTO service_item_option_value (option_id, value_code, value_name, extra_price,
                                       duration_delta, sort_order, status)
SELECT o.id, 'WINDOW', '玻璃清洗', 60.00, 20, 2, 'ACTIVE'
FROM service_item_option o
JOIN service_item i ON i.id = o.service_item_id
WHERE i.item_no = 'SVC202610230001' AND o.option_code = 'ADDON';

-- 规格组：深度保洁的面积档（必选，单选）
--
-- numeric_value 取<b>档位下界</b>而不是上界：价格阶梯是左闭右开 [min, max)，
-- 如果"60 平米以内"填 60，就会落进 [60, 90) 那一档，报价直接错一档。
-- 取 0 / 60 / 90 / 120 之后，每个选项都精确落在自己那一档的左端点上。
INSERT INTO service_item_option (service_item_id, option_code, option_name, selection_type,
                                 required, min_select, max_select, sort_order, status)
SELECT i.id, 'AREA', '房屋面积', 'SINGLE', 1, 1, 1, 1, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_item_option_value (option_id, value_code, value_name, numeric_value,
                                       extra_price, duration_delta, sort_order, status)
SELECT o.id, 'AREA_0_60', '60 平米以内', 0.00, 0.00, 0, 1, 'ACTIVE'
FROM service_item_option o
JOIN service_item i ON i.id = o.service_item_id
WHERE i.item_no = 'SVC202610230002' AND o.option_code = 'AREA';

INSERT INTO service_item_option_value (option_id, value_code, value_name, numeric_value,
                                       extra_price, duration_delta, sort_order, status)
SELECT o.id, 'AREA_60_90', '60-90 平米', 60.00, 0.00, 0, 2, 'ACTIVE'
FROM service_item_option o
JOIN service_item i ON i.id = o.service_item_id
WHERE i.item_no = 'SVC202610230002' AND o.option_code = 'AREA';

INSERT INTO service_item_option_value (option_id, value_code, value_name, numeric_value,
                                       extra_price, duration_delta, sort_order, status)
SELECT o.id, 'AREA_90_120', '90-120 平米', 90.00, 0.00, 0, 3, 'ACTIVE'
FROM service_item_option o
JOIN service_item i ON i.id = o.service_item_id
WHERE i.item_no = 'SVC202610230002' AND o.option_code = 'AREA';

INSERT INTO service_item_option_value (option_id, value_code, value_name, numeric_value,
                                       extra_price, duration_delta, sort_order, status)
SELECT o.id, 'AREA_120_PLUS', '120 平米以上', 120.00, 0.00, 0, 4, 'ACTIVE'
FROM service_item_option o
JOIN service_item i ON i.id = o.service_item_id
WHERE i.item_no = 'SVC202610230002' AND o.option_code = 'AREA';

-- 面积阶梯：三档固定价 + 最后一档按 6 元/平米计（最低按 120 平米起算，
-- 所以选中"120 平米以上"时计价器算出 120 × 6 = 720 元，这就是该档的起步价）。
INSERT INTO service_price_tier (service_item_id, tier_type, min_value, max_value,
                                fixed_price, unit_price, sort_order, status)
SELECT i.id, 'AREA_TIER', 0.00, 60.00, 399.00, NULL, 1, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_price_tier (service_item_id, tier_type, min_value, max_value,
                                fixed_price, unit_price, sort_order, status)
SELECT i.id, 'AREA_TIER', 60.00, 90.00, 499.00, NULL, 2, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_price_tier (service_item_id, tier_type, min_value, max_value,
                                fixed_price, unit_price, sort_order, status)
SELECT i.id, 'AREA_TIER', 90.00, 120.00, 599.00, NULL, 3, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_price_tier (service_item_id, tier_type, min_value, max_value,
                                fixed_price, unit_price, sort_order, status)
SELECT i.id, 'AREA_TIER', 120.00, NULL, NULL, 6.00, 4, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

-- 服务标准：日常保洁 6 项，服务前后各要求照片
INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '服务前', '服务前拍照', '拍摄厨房、卫生间、客厅整体现状', 1, 1, 1, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '清洁', '厨房台面清洁', '台面无油渍、无水痕', 1, 0, 2, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '清洁', '卫生间洁具清洁', '马桶、洗手台、淋浴区无污渍', 1, 0, 3, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '清洁', '地面清洁', '全屋地面吸尘后湿拖', 1, 0, 4, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '收尾', '垃圾清理', '清理服务产生的垃圾并带离', 1, 0, 5, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '收尾', '服务后拍照', '拍摄清洁后同一角度照片', 1, 1, 6, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230001';

-- 深度保洁的标准：种子里的服务项目都必须是 PUBLISHED，
-- 而"发布前必须有标准"是业务硬规则，所以这里也必须配上，否则种子数据自相矛盾。
INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '服务前', '服务前拍照', '拍摄厨房、卫生间、客厅整体现状', 1, 1, 1, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '清洁', '厨房深度清洁', '油烟机表面、灶台、瓷砖缝隙除油', 1, 0, 2, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '清洁', '卫生间深度清洁', '洁具除垢、瓷砖缝隙刷洗、地漏清理', 1, 0, 3, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';

INSERT INTO service_standard_item (service_item_id, standard_group, item_name, description,
                                   required, evidence_required, sort_order, status)
SELECT i.id, '收尾', '服务后拍照', '拍摄清洁后同一角度照片', 1, 1, 4, 'ACTIVE'
FROM service_item i WHERE i.item_no = 'SVC202610230002';
