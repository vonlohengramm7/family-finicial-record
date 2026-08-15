package com.familyledger.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekStateCollectorTest {
    @Test
    void redactsKeyNamesWhileKeepingPerModelUsage() {
        String output = "printf '%s\\n' '[KEY] source-private-label' '  deepseek-chat 输入: 1,200 输出: 34 请求: 5 ¥0.12' '---METADATA: by_key_cost=0.12 by_key_req=5 source=api---'";
        StateDomainResponse response = new DeepSeekStateCollector(output).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getData()).containsEntry("by_key_cost", "0.12");
        assertThat(response.getData().toString()).contains("api-key-1", "deepseek-chat", "inputTokens=1200");
        assertThat(response.getData().toString()).doesNotContain("source-private-label");
    }
}
