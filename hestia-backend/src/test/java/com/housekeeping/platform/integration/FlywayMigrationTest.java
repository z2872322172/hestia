package com.housekeeping.platform.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移集成测试：验证"空库一次迁移成功、重复执行幂等、6 张技术表齐全"。
 *
 * <p>必须用真实 MySQL 而不是 H2：CHECK 约束、JSON 列、utf8mb4 排序规则在 H2 上行为不同，
 * 用 H2 通过不代表 MySQL 通过（docs/03 第 3.3 节明确不选 H2）。</p>
 *
 * <p>标记 {@code integration}：默认 {@code mvn clean verify} 不执行，需要 Docker；
 * 无 Docker 时 Testcontainers 会自动跳过而不是失败。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.40"))
            .withDatabaseName("hestia")
            .withUsername("hestia")
            .withPassword("hestia-dev-secret");

    @Test
    @DisplayName("空库执行 V1 迁移后 6 张技术表全部存在，重复执行不重复建表")
    void migratesFromEmptyDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .encoding("UTF-8")
                .load();

        MigrateResult firstRun = flyway.migrate();
        assertThat(firstRun.migrationsExecuted).isEqualTo(1);

        // 第二次启动不应重复执行已成功的迁移，否则说明版本号或校验和有问题
        MigrateResult secondRun = flyway.migrate();
        assertThat(secondRun.migrationsExecuted).isZero();

        flyway.validate();

        List<String> tables = listTables();
        assertThat(tables).contains(
                "event_outbox",
                "consumer_message",
                "idempotency_record",
                "file_object",
                "audit_log",
                "system_config",
                "flyway_schema_history");
    }

    private List<String> listTables() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT table_name FROM information_schema.tables
                      WHERE table_schema = DATABASE()
                      ORDER BY table_name
                     """)) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        return tables;
    }
}
