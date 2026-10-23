-- ============================================================================
-- V2 账号、客户与位置（批次 1）
--
-- 表清单与创建顺序严格按 docs/07 第 10.2 节：
--   auth_role -> auth_permission -> auth_role_permission
--   -> account -> account_role -> account_area_scope -> account_invitation
--   -> account_login_log
--   -> customer_profile -> customer_address
--   -> administrative_region -> service_area -> service_area_coverage
--
-- 额外说明：
--   * business_no_sequence 是 V1 六个技术表之外的补充技术表，用于生成
--     account_no / customer_no / address_no / area_no 这类"日期 + 序列"业务编号。
--     不这样做就只能用随机数或"查询 count + 1"，前者不可读、后者并发不安全。
--   * audit_log / system_config 的 account 外键在 V1 中因建表顺序被推迟，
--     本版在 account 建表后补齐。
--   * 末尾的种子数据是"最小可演示字典"：4 个角色是身份模块的硬依赖，
--     行政区与服务区域用于让地址匹配链路在本地/演示环境能真正跑通，
--     真实环境由管理员通过导入接口覆盖（导入为幂等更新）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 角色字典
--    role_type 区分外部账号与内部账号：两者不允许混用，避免内部权限外溢。
-- ----------------------------------------------------------------------------
CREATE TABLE auth_role
(
    role_code            VARCHAR(32)     NOT NULL COMMENT '角色编码',
    role_name            VARCHAR(64)     NOT NULL COMMENT '角色名称',
    role_type            VARCHAR(16)     NOT NULL COMMENT 'EXTERNAL/INTERNAL',
    data_scope_type      VARCHAR(16)     NOT NULL COMMENT 'SELF/AREA/ALL',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    sort_order           INT             NOT NULL DEFAULT 0 COMMENT '排序',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (role_code),
    KEY idx_auth_role_type (role_type, status),
    CONSTRAINT chk_auth_role_type
        CHECK (role_type IN ('EXTERNAL', 'INTERNAL')),
    CONSTRAINT chk_auth_role_scope
        CHECK (data_scope_type IN ('SELF', 'AREA', 'ALL')),
    CONSTRAINT chk_auth_role_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色字典';

-- ----------------------------------------------------------------------------
-- 2. 权限点字典
--    首版只登记"接口级"权限点；数据范围由角色 data_scope_type + account_area_scope 决定。
-- ----------------------------------------------------------------------------
CREATE TABLE auth_permission
(
    permission_code      VARCHAR(64)     NOT NULL COMMENT '权限点编码',
    permission_name      VARCHAR(64)     NOT NULL COMMENT '权限点名称',
    module_code          VARCHAR(32)     NOT NULL COMMENT '所属模块',
    description          VARCHAR(255)    NULL COMMENT '说明',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (permission_code),
    KEY idx_auth_permission_module (module_code, status),
    CONSTRAINT chk_auth_permission_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='权限点字典';

-- ----------------------------------------------------------------------------
-- 3. 角色权限关联
-- ----------------------------------------------------------------------------
CREATE TABLE auth_role_permission
(
    role_code            VARCHAR(32)     NOT NULL COMMENT '角色编码',
    permission_code      VARCHAR(64)     NOT NULL COMMENT '权限点编码',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (role_code, permission_code),
    KEY idx_auth_role_permission_permission (permission_code),
    CONSTRAINT fk_auth_role_permission_role
        FOREIGN KEY (role_code) REFERENCES auth_role (role_code)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_auth_role_permission_permission
        FOREIGN KEY (permission_code) REFERENCES auth_permission (permission_code)
            ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色权限关联';

-- ----------------------------------------------------------------------------
-- 4. 登录账号
--    * 手机号唯一索引建在 (phone_country_code, phone)：号码只存数字，不存空格横线；
--    * password_hash 允许为空，但仅限 PENDING_ACTIVATION（邀请未激活）；
--    * token_version 是"账号级令牌失效"开关：改密、停用、角色暂停都 +1。
-- ----------------------------------------------------------------------------
CREATE TABLE account
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    account_no           VARCHAR(32)     NOT NULL COMMENT '对外账号编号',
    phone_country_code   VARCHAR(8)      NOT NULL DEFAULT '+86' COMMENT '国际区号',
    phone                VARCHAR(20)     NOT NULL COMMENT '归一化后的手机号（纯数字）',
    password_hash        VARCHAR(100)    NULL COMMENT 'BCrypt 结果，待激活账号为空',
    display_name         VARCHAR(64)     NOT NULL COMMENT '通用显示名称',
    avatar_url           VARCHAR(512)    NULL COMMENT '头像地址',
    status               VARCHAR(32)     NOT NULL DEFAULT 'PENDING_ACTIVATION'
        COMMENT 'PENDING_ACTIVATION/ACTIVE/LOCKED/DISABLED',
    token_version        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '令牌版本，变更即令旧 Token 失效',
    failed_login_count   SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until         DATETIME(3)     NULL COMMENT '锁定截止时间',
    last_login_at        DATETIME(3)     NULL COMMENT '最后登录时间',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_no (account_no),
    UNIQUE KEY uk_account_phone (phone_country_code, phone),
    KEY idx_account_status (status),
    KEY idx_account_last_login (last_login_at),
    CONSTRAINT chk_account_status
        CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_account_password_presence
        CHECK (status = 'PENDING_ACTIVATION' OR password_hash IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录账号';

-- ----------------------------------------------------------------------------
-- 5. 账号角色
--    同一账号 + 角色只保留一行；重新授予时更新 status，不重复插入。
-- ----------------------------------------------------------------------------
CREATE TABLE account_role
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id           BIGINT UNSIGNED NOT NULL COMMENT '账号 ID',
    role_code            VARCHAR(32)     NOT NULL COMMENT '角色编码',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/REVOKED',
    granted_by_account_id BIGINT UNSIGNED NULL COMMENT '授予人账号 ID',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    granted_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授予时间',
    revoked_at           DATETIME(3)     NULL COMMENT '撤销时间',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_role (account_id, role_code),
    KEY idx_account_role_role (role_code, status),
    KEY idx_account_role_granted_by (granted_by_account_id),
    CONSTRAINT fk_account_role_account
        FOREIGN KEY (account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_role
        FOREIGN KEY (role_code) REFERENCES auth_role (role_code)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_granted_by
        FOREIGN KEY (granted_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_account_role_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='账号角色';

-- ----------------------------------------------------------------------------
-- 6. 账号区域数据范围
--    service_area_id 属于位置模块，跨模块只保存 ID + 索引，不建级联外键。
-- ----------------------------------------------------------------------------
CREATE TABLE account_area_scope
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id           BIGINT UNSIGNED NOT NULL COMMENT '账号 ID',
    role_code            VARCHAR(32)     NOT NULL COMMENT '角色编码',
    service_area_id      BIGINT UNSIGNED NOT NULL COMMENT '服务区域 ID（位置模块）',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_area_scope (account_id, role_code, service_area_id),
    KEY idx_account_area_scope_role (role_code),
    KEY idx_account_area_scope_area (service_area_id),
    CONSTRAINT fk_account_area_scope_role
        FOREIGN KEY (account_id, role_code)
            REFERENCES account_role (account_id, role_code)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_account_area_scope_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='账号区域数据范围';

-- ----------------------------------------------------------------------------
-- 7. 邀请与一次性凭证
--    数据库只保存 token 的 SHA-256，凭证明文只在创建时返回一次。
-- ----------------------------------------------------------------------------
CREATE TABLE account_invitation
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id           BIGINT UNSIGNED NOT NULL COMMENT '账号 ID',
    role_code            VARCHAR(32)     NOT NULL COMMENT '目标角色',
    purpose              VARCHAR(32)     NOT NULL COMMENT 'WORKER_ACTIVATION/PASSWORD_RESET',
    token_hash           CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '凭证 SHA-256',
    status               VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/USED/EXPIRED/REVOKED',
    expires_at           DATETIME(3)     NOT NULL COMMENT '过期时间',
    used_at              DATETIME(3)     NULL COMMENT '使用时间',
    invited_by_account_id BIGINT UNSIGNED NULL COMMENT '邀请人账号 ID',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_invitation_token (token_hash),
    KEY idx_account_invitation_account (account_id, status, expires_at),
    KEY idx_account_invitation_expire (status, expires_at),
    CONSTRAINT fk_account_invitation_account
        FOREIGN KEY (account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_account_invitation_role
        FOREIGN KEY (role_code) REFERENCES auth_role (role_code)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_account_invitation_inviter
        FOREIGN KEY (invited_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_account_invitation_purpose
        CHECK (purpose IN ('WORKER_ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT chk_account_invitation_status
        CHECK (status IN ('PENDING', 'USED', 'EXPIRED', 'REVOKED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='邀请和一次性激活凭证';

-- ----------------------------------------------------------------------------
-- 8. 登录日志
--    login_key_hash = HMAC-SHA256(归一化手机号)：既不存明文，也不用裸 SHA256
--    （手机号空间小，裸 SHA256 可被彩虹表反查）。
-- ----------------------------------------------------------------------------
CREATE TABLE account_login_log
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id           BIGINT UNSIGNED NULL COMMENT '账号 ID，账号不存在时为空',
    login_key_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登录标识 HMAC',
    result               VARCHAR(16)     NOT NULL COMMENT 'SUCCESS/FAILED/LOCKED/DISABLED',
    reason_code          VARCHAR(32)     NULL COMMENT '失败原因码',
    ip_address           VARCHAR(45)     NULL COMMENT '客户端 IP',
    user_agent           VARCHAR(512)    NULL COMMENT '客户端 UA（截断保存）',
    trace_id             VARCHAR(64)     NULL COMMENT '链路标识',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_login_log_account_time (account_id, created_at),
    KEY idx_login_log_key_time (login_key_hash, created_at),
    KEY idx_login_log_result_time (result, created_at),
    CONSTRAINT fk_login_log_account
        FOREIGN KEY (account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_login_log_result
        CHECK (result IN ('SUCCESS', 'FAILED', 'LOCKED', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='账号登录日志';

-- ----------------------------------------------------------------------------
-- 9. 客户业务档案
--    不重复保存手机号与密码；account_id 唯一保证一个账号最多一个客户档案。
-- ----------------------------------------------------------------------------
CREATE TABLE customer_profile
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_no          VARCHAR(32)     NOT NULL COMMENT '对外客户编号',
    account_id           BIGINT UNSIGNED NOT NULL COMMENT '关联登录账号',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_profile_no (customer_no),
    UNIQUE KEY uk_customer_profile_account (account_id),
    KEY idx_customer_profile_status (status),
    CONSTRAINT fk_customer_profile_account
        FOREIGN KEY (account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_customer_profile_status
        CHECK (status IN ('ACTIVE', 'CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消费者业务档案';

-- ----------------------------------------------------------------------------
-- 10. 客户服务地址
--     active_default_customer_id 是生成列 + 唯一索引：同一客户最多一个有效默认地址，
--     这条约束必须由数据库兜底，应用层的"先清后设"在并发下会漏。
-- ----------------------------------------------------------------------------
CREATE TABLE customer_address
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    address_no           VARCHAR(32)     NOT NULL COMMENT '对外地址编号',
    customer_id          BIGINT UNSIGNED NOT NULL COMMENT '客户档案 ID',
    label                VARCHAR(32)     NOT NULL DEFAULT 'HOME' COMMENT 'HOME/PARENTS/OFFICE/RENTAL/OTHER',

    contact_name         VARCHAR(64)     NOT NULL COMMENT '联系人',
    contact_country_code VARCHAR(8)      NOT NULL DEFAULT '+86' COMMENT '联系人国际区号',
    contact_phone        VARCHAR(20)     NOT NULL COMMENT '联系人电话（纯数字）',

    province_code        VARCHAR(12)     NULL COMMENT '省编码',
    province_name        VARCHAR(64)     NULL COMMENT '省名称',
    city_code            VARCHAR(12)     NULL COMMENT '市编码',
    city_name            VARCHAR(64)     NULL COMMENT '市名称',
    district_code        VARCHAR(12)     NOT NULL COMMENT '区县编码',
    district_name        VARCHAR(64)     NOT NULL COMMENT '区县名称',
    street_code          VARCHAR(12)     NULL COMMENT '街道编码',
    street_name          VARCHAR(64)     NULL COMMENT '街道名称',

    service_area_id      BIGINT UNSIGNED NULL COMMENT '匹配到的服务区域 ID',
    area_match_status    VARCHAR(16)     NOT NULL DEFAULT 'UNMATCHED'
        COMMENT 'UNMATCHED/MATCHED/OUT_OF_RANGE/STALE',
    area_matched_at      DATETIME(3)     NULL COMMENT '最近一次匹配时间',

    community_name       VARCHAR(128)    NULL COMMENT '小区/社区',
    building_no          VARCHAR(64)     NULL COMMENT '楼栋',
    unit_no              VARCHAR(32)     NULL COMMENT '单元',
    floor_no             VARCHAR(16)     NULL COMMENT '楼层',
    room_no              VARCHAR(32)     NULL COMMENT '门牌',
    detail_address       VARCHAR(255)    NOT NULL COMMENT '详细地址补充说明',

    longitude            DECIMAL(10, 7)  NULL COMMENT '经度',
    latitude             DECIMAL(10, 7)  NULL COMMENT '纬度',
    access_note          VARCHAR(500)    NULL COMMENT '门禁/电梯/宠物等履约说明（敏感）',

    is_default           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认地址',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    last_used_at         DATETIME(3)     NULL COMMENT '最近使用时间，用于默认地址提升排序',

    active_default_customer_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE
                WHEN is_default = 1 AND status = 'ACTIVE' THEN customer_id
                ELSE NULL
                END
            ) STORED COMMENT '默认地址唯一约束辅助列',

    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at           DATETIME(3)     NULL COMMENT '软删除时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_address_no (address_no),
    UNIQUE KEY uk_customer_active_default (active_default_customer_id),
    KEY idx_customer_address_list (customer_id, status, is_default, last_used_at),
    KEY idx_customer_address_service_area (service_area_id, area_match_status),
    KEY idx_customer_address_district (district_code),
    CONSTRAINT fk_customer_address_customer
        FOREIGN KEY (customer_id) REFERENCES customer_profile (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_customer_address_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_customer_address_area_status
        CHECK (area_match_status IN ('UNMATCHED', 'MATCHED', 'OUT_OF_RANGE', 'STALE')),
    CONSTRAINT chk_customer_address_matched_area
        CHECK (area_match_status <> 'MATCHED' OR service_area_id IS NOT NULL),
    CONSTRAINT chk_customer_address_default
        CHECK (is_default IN (0, 1)),
    CONSTRAINT chk_customer_address_deleted_not_default
        CHECK (status = 'ACTIVE' OR is_default = 0),
    CONSTRAINT chk_customer_address_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_customer_address_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='客户服务地址';

-- ----------------------------------------------------------------------------
-- 11. 行政区字典
--     行政区只描述地址结构，不代表公司是否提供上门服务。
-- ----------------------------------------------------------------------------
CREATE TABLE administrative_region
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    region_code          VARCHAR(12)     NOT NULL COMMENT '国家行政区编码',
    parent_id            BIGINT UNSIGNED NULL COMMENT '上级行政区 ID',
    region_level         VARCHAR(16)     NOT NULL COMMENT 'PROVINCE/CITY/DISTRICT/STREET',
    region_name          VARCHAR(128)    NOT NULL COMMENT '行政区名称',
    short_name           VARCHAR(64)     NULL COMMENT '简称',
    full_name            VARCHAR(255)    NULL COMMENT '全称',
    longitude            DECIMAL(10, 7)  NULL COMMENT '中心经度',
    latitude             DECIMAL(10, 7)  NULL COMMENT '中心纬度',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    sort_order           INT             NOT NULL DEFAULT 0 COMMENT '排序',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_administrative_region_code (region_code),
    KEY idx_administrative_region_parent (parent_id, region_level, status),
    KEY idx_administrative_region_name (region_name),
    CONSTRAINT fk_administrative_region_parent
        FOREIGN KEY (parent_id) REFERENCES administrative_region (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_administrative_region_level
        CHECK (region_level IN ('PROVINCE', 'CITY', 'DISTRICT', 'STREET')),
    CONSTRAINT chk_administrative_region_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_administrative_region_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_administrative_region_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='行政区划字典';

-- ----------------------------------------------------------------------------
-- 12. 服务区域
--     priority 数值越大越优先；只有 ACTIVE 且在生效期内才参与匹配。
-- ----------------------------------------------------------------------------
CREATE TABLE service_area
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    area_no              VARCHAR(32)     NOT NULL COMMENT '对外服务区域编号',
    area_name            VARCHAR(128)    NOT NULL COMMENT '服务区域名称',
    description          VARCHAR(500)    NULL COMMENT '说明',
    city_region_id       BIGINT UNSIGNED NOT NULL COMMENT '城市行政区 ID',
    area_type            VARCHAR(32)     NOT NULL DEFAULT 'DISTRICT_GROUP'
        COMMENT 'DISTRICT_GROUP/STREET_GROUP/CUSTOM',
    priority             INT             NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
    status               VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/INACTIVE/ARCHIVED',
    center_longitude     DECIMAL(10, 7)  NULL COMMENT '中心经度',
    center_latitude      DECIMAL(10, 7)  NULL COMMENT '中心纬度',
    boundary_geojson     JSON            NULL COMMENT '预留地图边界',
    effective_from       DATETIME(3)     NULL COMMENT '生效开始时间',
    effective_to         DATETIME(3)     NULL COMMENT '生效结束时间',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_area_no (area_no),
    KEY idx_service_area_city_status (city_region_id, status, priority),
    KEY idx_service_area_effective (status, effective_from, effective_to),
    CONSTRAINT fk_service_area_city
        FOREIGN KEY (city_region_id) REFERENCES administrative_region (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_area_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_area_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_area_type
        CHECK (area_type IN ('DISTRICT_GROUP', 'STREET_GROUP', 'CUSTOM')),
    CONSTRAINT chk_service_area_priority
        CHECK (priority BETWEEN 0 AND 999),
    CONSTRAINT chk_service_area_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_service_area_effective_range
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_service_area_center_longitude
        CHECK (center_longitude IS NULL OR center_longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_service_area_center_latitude
        CHECK (center_latitude IS NULL OR center_latitude BETWEEN -90 AND 90)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='公司服务区域';

-- ----------------------------------------------------------------------------
-- 13. 服务区域覆盖范围
--     覆盖不物理删除：停用只改 status，保留配置历史。
-- ----------------------------------------------------------------------------
CREATE TABLE service_area_coverage
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    service_area_id      BIGINT UNSIGNED NOT NULL COMMENT '服务区域 ID',
    region_id            BIGINT UNSIGNED NOT NULL COMMENT '区县或街道行政区 ID',
    coverage_level       VARCHAR(16)     NOT NULL COMMENT 'DISTRICT/STREET',
    status               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by_account_id BIGINT UNSIGNED NULL COMMENT '创建账号 ID',
    updated_by_account_id BIGINT UNSIGNED NULL COMMENT '最后修改账号 ID',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_area_coverage (service_area_id, region_id),
    KEY idx_service_area_coverage_region (region_id, status, coverage_level),
    KEY idx_service_area_coverage_area (service_area_id, status),
    CONSTRAINT fk_service_area_coverage_area
        FOREIGN KEY (service_area_id) REFERENCES service_area (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_area_coverage_region
        FOREIGN KEY (region_id) REFERENCES administrative_region (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_area_coverage_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_service_area_coverage_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_service_area_coverage_level
        CHECK (coverage_level IN ('DISTRICT', 'STREET')),
    CONSTRAINT chk_service_area_coverage_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='服务区域覆盖范围';

-- ----------------------------------------------------------------------------
-- 14. 业务编号序列（技术表）
--     按 (seq_key, seq_date) 逐日重置；取号方式是"先 UPDATE 自增再读回"，
--     UPDATE 的行锁保证同一编号在并发下不会重复。
-- ----------------------------------------------------------------------------
CREATE TABLE business_no_sequence
(
    seq_key              VARCHAR(32)     NOT NULL COMMENT '业务类型：ACCOUNT/CUSTOMER/ADDRESS/SERVICE_AREA',
    seq_date             CHAR(8)         NOT NULL COMMENT '日期 yyyyMMdd，用于按天重置',
    current_value        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已用最大值',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (seq_key, seq_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='业务编号日序列';

-- ----------------------------------------------------------------------------
-- 15. 补齐 V1 延迟的 account 外键
--     审计表的操作人必须是真实账号；系统操作留空（NULL），因此允许为空。
-- ----------------------------------------------------------------------------
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_operator
        FOREIGN KEY (operator_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE system_config
    ADD CONSTRAINT fk_system_config_creator
        FOREIGN KEY (created_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_system_config_updater
        FOREIGN KEY (updated_by_account_id) REFERENCES account (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- ============================================================================
-- 种子数据
-- ============================================================================

-- 角色：4 个固定角色是身份模块的硬依赖（注册写 CUSTOMER、邀请写 WORKER、
-- 运营与管理员由后台创建）。不允许业务代码动态新增角色编码。
INSERT INTO auth_role (role_code, role_name, role_type, data_scope_type, status, sort_order)
VALUES ('CUSTOMER', '消费者', 'EXTERNAL', 'SELF', 'ACTIVE', 10),
       ('WORKER', '服务人员', 'EXTERNAL', 'SELF', 'ACTIVE', 20),
       ('OPERATOR', '运营人员', 'INTERNAL', 'AREA', 'ACTIVE', 30),
       ('ADMIN', '系统管理员', 'INTERNAL', 'ALL', 'ACTIVE', 40);

-- 权限点：只登记批次 1 真正会用到的接口权限；后续批次随接口一起新增。
INSERT INTO auth_permission (permission_code, permission_name, module_code, description, status)
VALUES ('auth:account:read', '查看账号', 'identity', '后台查询账号列表与详情', 'ACTIVE'),
       ('auth:account:disable', '停用账号', 'identity', '停用或启用账号', 'ACTIVE'),
       ('auth:role:manage', '管理角色与区域范围', 'identity', '调整账号角色与运营区域', 'ACTIVE'),
       ('workforce:worker:manage', '管理服务人员', 'workforce', '创建人员档案并发送激活邀请', 'ACTIVE'),
       ('location:service-area:manage', '管理服务区域', 'location', '维护服务区域与覆盖范围', 'ACTIVE');

INSERT INTO auth_role_permission (role_code, permission_code)
VALUES ('ADMIN', 'auth:account:read'),
       ('ADMIN', 'auth:account:disable'),
       ('ADMIN', 'auth:role:manage'),
       ('ADMIN', 'workforce:worker:manage'),
       ('ADMIN', 'location:service-area:manage'),
       ('OPERATOR', 'workforce:worker:manage'),
       ('OPERATOR', 'location:service-area:manage');

-- 行政区字典：最小演示集（广东省 -> 广州市 -> 天河区/越秀区 -> 五山街道/员村街道）。
-- 真实数据由管理员通过 /api/v1/admin/administrative-regions/import 幂等导入覆盖。
INSERT INTO administrative_region (id, region_code, parent_id, region_level, region_name, status, sort_order)
VALUES (1, '440000', NULL, 'PROVINCE', '广东省', 'ACTIVE', 1),
       (2, '440100', 1, 'CITY', '广州市', 'ACTIVE', 1),
       (3, '440106', 2, 'DISTRICT', '天河区', 'ACTIVE', 1),
       (4, '440104', 2, 'DISTRICT', '越秀区', 'ACTIVE', 2),
       (5, '440106001', 3, 'STREET', '五山街道', 'ACTIVE', 1),
       (6, '440106002', 3, 'STREET', '员村街道', 'ACTIVE', 2);

-- 服务区域：两个片区用于演示"街道覆盖优先于区县覆盖"。
--   片区 A 覆盖整个天河区（区县覆盖，priority 100）
--   片区 B 只覆盖五山街道（街道覆盖，priority 50）
-- 五山街道的地址应命中 B，员村街道的地址应命中 A。
INSERT INTO service_area (id, area_no, area_name, description, city_region_id, area_type,
                          priority, status, effective_from)
VALUES (1, 'SA202610230001', '天河区核心片区', '演示数据：覆盖整个天河区', 2, 'DISTRICT_GROUP',
        100, 'ACTIVE', CURRENT_TIMESTAMP(3)),
       (2, 'SA202610230002', '天河区五山片区', '演示数据：仅覆盖五山街道', 2, 'STREET_GROUP',
        50, 'ACTIVE', CURRENT_TIMESTAMP(3));

INSERT INTO service_area_coverage (service_area_id, region_id, coverage_level, status)
VALUES (1, 3, 'DISTRICT', 'ACTIVE'),
       (2, 5, 'STREET', 'ACTIVE');
