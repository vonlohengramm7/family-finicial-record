package com.familyledger.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BabyStateCollectorTest {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T04:00:00Z"), BEIJING);

    @TempDir
    Path tempDir;

    @Test
    void usesTodayReportAndExposesOnlyStructuredSummary() throws Exception {
        Files.writeString(tempDir.resolve("2026-08-15.md"), "# 今日日报\n\n## 喂养记录\n- 奶量 750ml\n\n## 近期计划\n- [ ] 观察米粉耐受\n");

        BabyStateCollector collector = new BabyStateCollector(tempDir, CLOCK);

        StateDomainResponse response = collector.collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getSource()).isEqualTo("baby-daily-report:2026-08-15.md");
        assertThat(response.getData()).containsEntry("reportDate", "2026-08-15");
        assertThat(response.getData()).containsKey("todaySummary");
        assertThat(response.getData()).doesNotContainKey("rawMarkdown");
        assertThat(response.getData().get("todos").toString()).contains("观察米粉耐受");
    }

    @Test
    void fallsBackToLatestReportAsStaleWhenTodayIsMissing() throws Exception {
        Files.writeString(tempDir.resolve("2026-08-14.md"), "# 昨日日报\n\n## 近期计划\n- [ ] 复测体重\n");

        StateDomainResponse response = new BabyStateCollector(tempDir, CLOCK).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.STALE);
        assertThat(response.getData()).containsEntry("reportDate", "2026-08-14");
        assertThat(response.getErrorCode()).isEqualTo("TODAY_REPORT_MISSING");
    }
}
