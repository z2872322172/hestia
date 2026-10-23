-- ============================================================================
-- 本地 MySQL 初始化脚本（容器首次启动时执行一次）
--
-- 表结构不在这里创建：全部交给应用启动时的 Flyway 迁移，
-- 保证"从空库重建"与"线上增量升级"走同一条路径。
-- ============================================================================

-- 业务库（MYSQL_DATABASE 已创建，这里只兜底保证字符集正确）
CREATE DATABASE IF NOT EXISTS `hestia`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 集成测试库：Testcontainers 不可用时的本机替代方案，与业务库隔离
CREATE DATABASE IF NOT EXISTS `hestia_test`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 开发账号只授予业务库权限，避免用 root 跑应用
GRANT ALL PRIVILEGES ON `hestia`.* TO 'hestia'@'%';
GRANT ALL PRIVILEGES ON `hestia_test`.* TO 'hestia'@'%';
FLUSH PRIVILEGES;
