package com.familyledger.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CodexStateCollector implements StateCollector {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final long TTL_SECONDS = 3_600;
    private final Path stateFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodexStateCollector(@Value("${family-state.codex.state-file:/home/vonlohengramm/.hermes/data/gpt-plus-usage-state.json}") String stateFile) { this.stateFile = Path.of(stateFile); }
    @Override public String domain() { return "codex"; }

    @Override
    public StateDomainResponse collect() {
        try {
            if (!Files.isRegularFile(stateFile)) return unavailable("CODEX_SOURCE_UNAVAILABLE", "未找到 Codex 用量采集快照");
            Map<String, Object> raw = objectMapper.readValue(Files.readString(stateFile), new TypeReference<>() {});
            Map<String, Object> data = new LinkedHashMap<>();
            copy(raw, data, "session_used_pct", "sessionUsedPercent");
            copy(raw, data, "weekly_used_pct", "weeklyUsedPercent");
            copy(raw, data, "last_check", "lastCheck");
            if (data.isEmpty()) return unavailable("CODEX_SOURCE_UNAVAILABLE", "Codex 用量采集快照没有可展示字段");
            LocalDateTime observed = LocalDateTime.ofInstant(Files.getLastModifiedTime(stateFile).toInstant(), BEIJING);
            LocalDateTime expires = observed.plusSeconds(TTL_SECONDS);
            boolean expired = expires.isBefore(LocalDateTime.now(BEIJING));
            return StateDomainResponse.builder().status(expired ? StateStatus.STALE : StateStatus.FRESH).source("codex-usage-monitor")
                    .observedAt(observed).freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).expiresAt(expires).build())
                    .data(data).errorCode(expired ? "CODEX_SNAPSHOT_EXPIRED" : null)
                    .errorMessage(expired ? "Codex 用量采集快照已过期" : null).nextRefreshAt(expires).build();
        } catch (Exception exception) { return unavailable("CODEX_SOURCE_UNAVAILABLE", "Codex 用量采集快照无法读取"); }
    }
    private void copy(Map<String, Object> from, Map<String, Object> to, String source, String target) { if (from.get(source) != null) to.put(target, from.get(source)); }
    private StateDomainResponse unavailable(String code, String message) { return StateDomainResponse.builder().status(StateStatus.UNAVAILABLE).source("codex-usage-monitor")
            .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).build()).data(Map.of()).errorCode(code).errorMessage(message).build(); }
}
