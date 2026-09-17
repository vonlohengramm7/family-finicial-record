package com.familyledger.state;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫问界能耗百分比字段的三位小数精度，避免后续 schema 变更将 10.5% 再截断为 0.11。
 */
class EnergyLogPrecisionMigrationContractTest {
    private static final String MIGRATION = "/db/migration/V4__increase_energy_percentage_precision.sql";
    private static final List<String> PERCENTAGE_COLUMNS = List.of(
            "soc_before", "soc_after", "fuel_before", "fuel_after");

    private static String loadMigrationSql() throws IOException {
        try (InputStream in = EnergyLogPrecisionMigrationContractTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("迁移文件必须存在于 classpath: %s", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String executableSql(String sql) {
        String noBlock = sql.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlock.replaceAll("(?m)--[^\\n]*", "");
    }

    @Test
    void migrationKeepsAllPercentageColumnsAtThreeDecimalPlaces() throws IOException {
        String sql = loadMigrationSql();
        for (String column : PERCENTAGE_COLUMNS) {
            assertThat(sql).as("迁移缺少三位小数定义: %s", column)
                    .matches("(?is).*\\b" + column + "\\b\\s+DECIMAL\\s*\\(\\s*5\\s*,\\s*3\\s*\\).*?");
        }
    }

    @Test
    void migrationChangesOnlyEnergyLogColumnDefinitions() throws IOException {
        String sql = executableSql(loadMigrationSql());
        assertThat(sql).matches("(?is)^\\s*ALTER\\s+TABLE\\s+energy_log\\b.*;\\s*$");
        for (String keyword : List.of("INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE")) {
            assertThat(sql).as("精度迁移不得修改或删除既有数据: %s", keyword)
                    .doesNotMatch("(?is).*\\b" + keyword + "\\b.*");
        }
    }
}
