package com.familyledger.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeGoStateCollectorTest {

    @Test
    void parsesOnlyUsableQuotaWindowsFromUsageApiJson() {
        String command = "printf '%s' '{\"usage\":{\"rolling\":{\"status\":\"ok\",\"percent\":14,\"resetsAt\":\"2026-08-17T06:28:17.066Z\"},\"weekly\":{\"status\":\"ok\",\"percent\":5,\"resetsAt\":\"2026-08-24T00:00:00.066Z\"},\"monthly\":{\"status\":\"ok\",\"percent\":2,\"resetsAt\":\"2026-09-17T01:10:32.066Z\"}}}'";

        StateDomainResponse response = new OpenCodeGoStateCollector(command).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getSource()).isEqualTo("opencode-go:usage-api");
        assertThat(response.getData()).containsOnlyKeys("rolling", "weekly", "monthly");
        assertThat(response.getData().toString()).contains("percent=14", "resetsAt=2026-08-17T14:28:17.066");
        assertThat(response.getData().toString()).doesNotContain("Authorization", "Bearer", "OPENCODE_GO_API_KEY");
    }

    @Test
    void marksDomainUnavailableWhenNoQuotaWindowIsUsable() {
        StateDomainResponse response = new OpenCodeGoStateCollector("printf '%s' '{\"usage\":{\"weekly\":{\"status\":\"limited\"}}}'").collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(response.getErrorCode()).isEqualTo("OPENCODE_GO_USAGE_UNAVAILABLE");
        assertThat(response.getData()).isEmpty();
    }
}
