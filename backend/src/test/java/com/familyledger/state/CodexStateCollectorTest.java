package com.familyledger.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CodexStateCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void marksExpiredUsageSnapshotAsStale() throws Exception {
        Path snapshot = tempDir.resolve("gpt-plus-usage-state.json");
        Files.writeString(snapshot, "{\"weekly_used_pct\":5,\"last_check\":\"2026-08-15T10:00:00+08:00\"}");
        Files.setLastModifiedTime(snapshot, FileTime.from(Instant.now().minusSeconds(3_601)));

        StateDomainResponse response = new CodexStateCollector(snapshot.toString()).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.STALE);
        assertThat(response.getData()).containsEntry("weeklyUsedPercent", 5);
        assertThat(response.getErrorCode()).isEqualTo("CODEX_SNAPSHOT_EXPIRED");
    }

    @Test
    void parsesRealStateFileWithNullSessionField() throws Exception {
        // 真实 gpt-plus-usage-state.json（2026-08-15 21:07 实测）：session_used_pct 为 null
        Path snapshot = tempDir.resolve("gpt-plus-usage-state.json");
        Files.writeString(snapshot, """
                {
                  "session_used_pct": null,
                  "weekly_used_pct": 24,
                  "last_check": "2026-08-15 21:07"
                }
                """);
        Files.setLastModifiedTime(snapshot, FileTime.from(Instant.now()));

        StateDomainResponse response = new CodexStateCollector(snapshot.toString()).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getData()).containsEntry("weeklyUsedPercent", 24);
        assertThat(response.getData()).containsEntry("lastCheck", "2026-08-15 21:07");
        assertThat(response.getData()).doesNotContainKey("sessionUsedPercent");
    }

    @Test
    void returnsUnavailableWhenStateFileMissing() {
        StateDomainResponse response = new CodexStateCollector(tempDir.resolve("missing.json").toString()).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(response.getErrorCode()).isEqualTo("CODEX_SOURCE_UNAVAILABLE");
        assertThat(response.getData()).isEmpty();
    }
}
