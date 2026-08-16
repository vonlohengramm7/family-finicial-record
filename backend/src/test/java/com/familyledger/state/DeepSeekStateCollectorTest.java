package com.familyledger.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekStateCollectorTest {

    // 真实脚本 deepseek-usage-query.py --date 2026-08-15 --by-key 的输出骨架（2026-08-15 实测 exit 0）。
    // 含 4 个 METADATA 块：sqlite 用量总览、平台 API 费用、GPT Plus/Codex 周用量、按 Key 明细。
    private static final String REAL_OUTPUT_CMD = "printf '%s\\n' "
            + "'--- 用量总览 (北京时间) ---' "
            + "'---METADATA: sessions=15 total_tokens=22919180 days=1 source=sqlite---' "
            + "'--- 消费明细 (平台API) ---' "
            + "'---METADATA: cost=2.20 days=1 source=api---' "
            + "'--- GPT Plus / Codex 周用量 ---' "
            + "'---METADATA: gpt_plus_usage=available source=openai-codex---' "
            + "'  [KEY] Hermes' "
            + "'     deepseek-v4-flash              输入:   1,489,446  输出: 466,630  请求:  104  ¥1.30' "
            + "'  [KEY] hermes qi' "
            + "'     deepseek-v4-flash              输入:   7,835,584  输出: 130,514  请求:  104  ¥0.90' "
            + "'---METADATA: by_key_cost=2.20 by_key_req=208---'";

    @Test
    void redactsKeyNamesWhileKeepingPerModelUsage() {
        String output = "printf '%s\\n' '[KEY] source-private-label' '  deepseek-chat 输入: 1,200 输出: 34 请求: 5 ¥0.12' '---METADATA: by_key_cost=0.12 by_key_req=5 source=api---'";
        StateDomainResponse response = new DeepSeekStateCollector(output).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getData()).containsEntry("by_key_cost", "0.12");
        assertThat(response.getData().toString()).contains("api-key-1", "deepseek-chat", "inputTokens=1200");
        assertThat(response.getData().toString()).doesNotContain("source-private-label");
    }

    @Test
    void doesNotLeakGptPlusMetadataIntoDeepSeekData() {
        StateDomainResponse response = new DeepSeekStateCollector(REAL_OUTPUT_CMD).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getData()).doesNotContainKey("gpt_plus_usage");
        assertThat(response.getData()).doesNotContainValue("openai-codex");
        assertThat(response.getData()).containsEntry("by_key_cost", "2.20");
        assertThat(response.getData()).containsEntry("by_key_req", "208");
    }

    @Test
    void neverLeaksGptBlockEvenWhenSourceHasNoHyphen() {
        // 现有实现的正则依赖 openai-codex 的连字符碰巧跳过 GPT 块；
        // 若 GPT 块的 source 改为无连字符值（如 codex），必须仍被显式忽略。
        String cmd = "printf '%s\\n' "
                + "'---METADATA: gpt_plus_usage=available source=codex---' "
                + "'---METADATA: cost=2.20 days=1 source=api---'";
        StateDomainResponse response = new DeepSeekStateCollector(cmd).collect();

        assertThat(response.getData()).doesNotContainKey("gpt_plus_usage");
        assertThat(response.getData()).doesNotContainValue("codex");
        assertThat(response.getData()).containsEntry("cost", "2.20");
    }

    @Test
    void keepsSqliteAndApiMetadataSeparatelyMarked() {
        StateDomainResponse response = new DeepSeekStateCollector(REAL_OUTPUT_CMD).collect();

        // SQLite 统计与平台 API 费用是独立通道，不得互相覆盖 source 标记。
        assertThat(response.getData()).containsEntry("sessions", "15");
        assertThat(response.getData()).containsEntry("cost", "2.20");
        assertThat(response.getData()).doesNotContainEntry("source", "openai-codex");
    }

    @Test
    void parsesRealByKeyBlocksWithoutExposingKeyLabels() {
        StateDomainResponse response = new DeepSeekStateCollector(REAL_OUTPUT_CMD).collect();

        assertThat(response.getData().get("byApiKey").toString())
                .contains("api-key-1", "api-key-2", "deepseek-v4-flash")
                .doesNotContain("Hermes");
    }

    @Test
    void returnsUnavailableWhenScriptFailsWithoutFabricatedValues() {
        StateDomainResponse response = new DeepSeekStateCollector("false").collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(response.getErrorCode()).isEqualTo("DEEPSEEK_SOURCE_UNAVAILABLE");
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void returnsUnavailableOnTimeoutWithoutFabricatedValues() {
        // bash -lc 会附加 --date/--by-key 参数；用 sh -c 包裹使多余参数被忽略，
        // sleep 60 超过 45s 超时阈值 → 强制销毁进程并返回 unavailable
        StateDomainResponse response = new DeepSeekStateCollector("sh -c 'sleep 60'").collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(response.getErrorCode()).isEqualTo("DEEPSEEK_TIMEOUT");
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void returnsUnavailableWhenOutputUnparsable() {
        StateDomainResponse response = new DeepSeekStateCollector("printf '%s\\n' 'not-a-metadata-block'").collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(response.getErrorCode()).isEqualTo("DEEPSEEK_PARSE_FAILED");
        assertThat(response.getData()).isEmpty();
    }
}
