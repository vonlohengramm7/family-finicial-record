package com.familyledger.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OpenCodeGoStateCollector implements StateCollector {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final long TTL_SECONDS = 300;
    private static final String SOURCE = "opencode-go:usage-api";
    private final String command;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenCodeGoStateCollector(@Value("${family-state.opencode-go.command:python3 /family-state/opencode/opencode-go-usage-query.py --raw-json}") String command) {
        this.command = command;
    }

    @Override public String domain() { return "opencodeGo"; }

    @Override
    public StateDomainResponse collect() {
        try {
            Process process = new ProcessBuilder("bash", "-lc", command).redirectErrorStream(true).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return unavailable("OPENCODE_GO_USAGE_TIMEOUT", "OpenCode Go 额度采集超时");
            }
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().reduce("", (left, right) -> left + right);
            }
            if (process.exitValue() != 0) return unavailable("OPENCODE_GO_USAGE_UNAVAILABLE", "OpenCode Go 额度采集失败");
            Map<String, Object> windows = usableWindows(output);
            if (windows.isEmpty()) return unavailable("OPENCODE_GO_USAGE_UNAVAILABLE", "OpenCode Go 未返回可用额度窗口");

            LocalDateTime observed = LocalDateTime.now(BEIJING);
            LocalDateTime expires = observed.plusSeconds(TTL_SECONDS);
            return StateDomainResponse.builder().status(StateStatus.FRESH).source(SOURCE).observedAt(observed)
                    .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).expiresAt(expires).build())
                    .data(windows).nextRefreshAt(expires).build();
        } catch (Exception exception) {
            return unavailable("OPENCODE_GO_USAGE_UNAVAILABLE", "OpenCode Go 额度采集不可用");
        }
    }

    private Map<String, Object> usableWindows(String output) throws Exception {
        Map<String, Object> root = objectMapper.readValue(output, new TypeReference<>() {});
        Object usage = root.get("usage");
        if (!(usage instanceof Map<?, ?> rawUsage)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : new String[]{"rolling", "weekly", "monthly"}) {
            Object candidate = rawUsage.get(name);
            if (!(candidate instanceof Map<?, ?> window) || !"ok".equals(window.get("status")) || !(window.get("percent") instanceof Number percent)) continue;
            Map<String, Object> safeWindow = new LinkedHashMap<>();
            safeWindow.put("percent", percent);
            if (window.get("resetsAt") instanceof String resetsAt) safeWindow.put("resetsAt", beijingTime(resetsAt));
            result.put(name, safeWindow);
        }
        return result;
    }

    private String beijingTime(String value) {
        try { return OffsetDateTime.parse(value).atZoneSameInstant(BEIJING).toLocalDateTime().toString(); }
        catch (Exception ignored) { return value; }
    }

    private StateDomainResponse unavailable(String code, String message) {
        return StateDomainResponse.builder().status(StateStatus.UNAVAILABLE).source(SOURCE)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).build()).data(Map.of())
                .errorCode(code).errorMessage(message).build();
    }
}
