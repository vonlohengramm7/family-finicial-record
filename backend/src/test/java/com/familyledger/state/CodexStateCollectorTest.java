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
}
