package com.familyledger.state;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K1 契约守卫（t_19fc0b96 存储层验收）：
 * 1) state_snapshot 迁移必须包含全部必需字段（domain/key/value/source/observed_at/fresh_until/status/error）
 * 2) 迁移必须可重复执行（CREATE TABLE IF NOT EXISTS 幂等守卫）
 * 3) 迁移不得引用或修改任何生产财务表，不得包含破坏性/写入型语句
 *
 * 对应 Python 侧 scripts/test_migrate_state.py 的同一组约束。
 */
class StateSnapshotMigrationContractTest {

    private static final String MIGRATION = "/db/migration/V3__create_state_snapshot.sql";

    /** K1 §4.1 与任务要求的必需字段（error 拆为 error_code + error_message）。 */
    private static final List<String> REQUIRED_FIELDS = List.of(
            "domain", "snapshot_key", "payload_json", "source",
            "observed_at", "fresh_until", "status",
            "error_code", "error_message");

    private static final List<String> FINANCIAL_TABLES = List.of(
            "transaction", "category", "user", "auto_transaction",
            "project", "energy_log", "vehicle_expense");

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "DROP", "TRUNCATE", "ALTER", "INSERT", "UPDATE", "DELETE", "REPLACE");

    private static String loadMigrationSql() throws IOException {
        try (InputStream in = StateSnapshotMigrationContractTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("迁移文件必须存在于 classpath: %s", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 剥离注释后的可执行 SQL（回滚说明等注释不参与破坏性语句检测）。 */
    private static String executableSql(String sql) {
        String noBlock = sql.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlock.replaceAll("(?m)--[^\\n]*", "");
    }

    @Test
    void migrationContainsAllRequiredFields() throws IOException {
        String sql = loadMigrationSql();
        for (String field : REQUIRED_FIELDS) {
            assertThat(sql).as("迁移缺少必需字段: %s", field)
                    .matches("(?is).*\\b" + java.util.regex.Pattern.quote(field) + "\\b.*");
        }
    }

    @Test
    void migrationIsIdempotentWithCreateIfNotExists() throws IOException {
        String sql = loadMigrationSql();
        assertThat(sql).matches("(?is).*CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+state_snapshot.*");
    }

    @Test
    void migrationDoesNotReferenceFinancialTables() throws IOException {
        String sql = executableSql(loadMigrationSql());
        for (String table : FINANCIAL_TABLES) {
            assertThat(sql).as("迁移不得引用财务表: %s", table)
                    .doesNotMatch("(?is).*\\b" + java.util.regex.Pattern.quote(table) + "\\b.*");
        }
    }

    @Test
    void migrationContainsNoDestructiveOrWriteStatements() throws IOException {
        String sql = executableSql(loadMigrationSql());
        for (String keyword : FORBIDDEN_KEYWORDS) {
            assertThat(sql).as("迁移不得包含破坏性/写入型关键字: %s", keyword)
                    .doesNotMatch("(?is).*\\b" + keyword + "\\b.*");
        }
    }

    @Test
    void migrationDeclaresUniqueKeyOnDomainAndKey() throws IOException {
        String sql = loadMigrationSql();
        assertThat(sql).matches("(?is).*uk_state_snapshot_domain_key\\s*\\(domain,\\s*snapshot_key\\).*");
    }
}
