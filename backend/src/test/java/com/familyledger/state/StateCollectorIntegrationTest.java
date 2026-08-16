package com.familyledger.state;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 采集器集成测试：直接调用真实数据源（DeepSeek 脚本、Codex 状态文件、汤圆日报/健康文件）。
 * 真实源不可用时跳过（assumeTrue），绝不伪造数据。仅在本机具备真实源时执行。
 */
class StateCollectorIntegrationTest {

    private static final Path DEEPSEEK_SCRIPT = Path.of("/home/vonlohengramm/.hermes/scripts/deepseek-usage-query.py");
    private static final Path CODEX_STATE = Path.of("/home/vonlohengramm/.hermes/data/gpt-plus-usage-state.json");
    private static final Path DAILY_LOG_DIR = Path.of("/home/vonlohengramm/records/baby-daily-reports/daily-logs");
    private static final Path HEALTH_RECORD = Path.of("/home/vonlohengramm/records/汤圆/health/health-record.md");
    private static final Path WEIGHT_TIMELINE = Path.of("/home/vonlohengramm/records/汤圆/health/weight-timeline.md");

    @Test
    void deepseekCollectorParsesRealScriptOutput() {
        assumeTrue(Files.isExecutable(DEEPSEEK_SCRIPT), "DeepSeek 采集脚本不存在，跳过集成测试");

        StateDomainResponse response = new DeepSeekStateCollector(
                "python3 " + DEEPSEEK_SCRIPT).collect();

        // 真实脚本要么成功返回数据（fresh），要么明确不可用；绝不含 GPT 域外来字段。
        assertThat(response.getStatus()).isIn(StateStatus.FRESH, StateStatus.UNAVAILABLE);
        assertThat(response.getSource()).isEqualTo("deepseek-platform-api-export");
        if (response.getStatus() == StateStatus.FRESH) {
            assertThat(response.getData().keySet()).doesNotContain("gpt_plus_usage");
            assertThat(response.getData().values()).doesNotContain("openai-codex");
            // 按 Key 明细（如可用）必须脱敏：api-key-N 标签，不含真实 Key 名称
            if (response.getData().containsKey("byApiKey")) {
                assertThat(response.getData().get("byApiKey").toString()).doesNotContain("sk-");
            }
        } else {
            assertThat(response.getErrorCode()).isNotBlank();
        }
    }

    @Test
    void codexCollectorReadsRealStateFile() throws Exception {
        assumeTrue(Files.isRegularFile(CODEX_STATE), "Codex 状态文件不存在，跳过集成测试");

        StateDomainResponse response = new CodexStateCollector(CODEX_STATE.toString()).collect();

        assertThat(response.getSource()).isEqualTo("codex-usage-monitor");
        assertThat(response.getStatus()).isIn(StateStatus.FRESH, StateStatus.STALE);
        // 真实状态文件必须有周窗口使用率
        assertThat(response.getData()).containsKey("weeklyUsedPercent");
        // 不得泄露凭证类字段
        assertThat(response.getData().keySet()).doesNotContain("token", "accessToken", "auth");
    }

    @Test
    void babyCollectorReadsRealReportsAndHealth() {
        assumeTrue(Files.isDirectory(DAILY_LOG_DIR), "汤圆日报目录不存在，跳过集成测试");
        assumeTrue(Files.isRegularFile(HEALTH_RECORD), "健康总览不存在，跳过集成测试");
        assumeTrue(Files.isRegularFile(WEIGHT_TIMELINE), "体重时间线不存在，跳过集成测试");

        BabyStateCollector collector = new BabyStateCollector(DAILY_LOG_DIR, HEALTH_RECORD, WEIGHT_TIMELINE,
                java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
        StateDomainResponse response = collector.collect();

        // 今天（2026-08-15）日报存在 → fresh；即便不是当天也至少 stale，绝不 unavailable
        assertThat(response.getStatus()).isIn(StateStatus.FRESH, StateStatus.STALE);
        assertThat(response.getData()).containsKey("reportDate");
        assertThat(response.getData()).containsKey("todaySummary");
        assertThat(response.getData()).containsKey("trends");
        assertThat(response.getData().get("trends").toString()).contains("weightSource");
        assertThat(response.getData().toString()).doesNotContain("rawMarkdown");
    }
}
