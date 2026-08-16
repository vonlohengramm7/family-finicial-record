package com.familyledger.state;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DeepSeekStateCollector implements StateCollector {
    private static final long TTL_SECONDS = 86_400;
    private final String command;

    public DeepSeekStateCollector(@Value("${family-state.deepseek.command:python3 /home/vonlohengramm/.hermes/scripts/deepseek-usage-query.py}") String command) { this.command = command; }
    @Override public String domain() { return "deepseek"; }

    @Override
    public StateDomainResponse collect() {
        try {
            Process process = new ProcessBuilder("bash", "-lc", command + " --date " + LocalDate.now() + " --by-key")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); return unavailable("DEEPSEEK_TIMEOUT", "DeepSeek 用量采集超时"); }
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) { output = reader.lines().reduce("", (a, b) -> a + "\n" + b); }
            if (process.exitValue() != 0) return unavailable("DEEPSEEK_SOURCE_UNAVAILABLE", "DeepSeek 平台用量采集失败");
            Map<String, Object> data = metadata(output);
            if (data.isEmpty()) return unavailable("DEEPSEEK_PARSE_FAILED", "DeepSeek 平台输出无法识别");
            data.put("byApiKey", byApiKey(output));
            LocalDateTime observed = LocalDateTime.now(); LocalDateTime expires = observed.plusSeconds(TTL_SECONDS);
            return StateDomainResponse.builder().status(StateStatus.FRESH).source("deepseek-platform-api-export")
                    .observedAt(observed).freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).expiresAt(expires).build())
                    .data(data).nextRefreshAt(expires).build();
        } catch (Exception exception) { return unavailable("DEEPSEEK_SOURCE_UNAVAILABLE", "DeepSeek 平台用量采集不可用"); }
    }
    private Map<String, Object> metadata(String output) {
        Map<String, Object> data = new LinkedHashMap<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("---METADATA: ([^-]+)---").matcher(output);
        while (matcher.find()) {
            String block = matcher.group(1).trim();
            // GPT Plus/Codex 周用量块属于 codex 域，显式忽略，不得混入 DeepSeek 数据。
            if (block.contains("gpt_plus_usage")) continue;
            for (String token : block.split("\\s+")) {
                String[] pair = token.split("=", 2);
                if (pair.length == 2 && !pair[0].equals("source")) data.put(pair[0], pair[1]);
            }
        }
        return data;
    }
    private java.util.List<Map<String, Object>> byApiKey(String output) {
        java.util.List<Map<String, Object>> keys = new java.util.ArrayList<>();
        java.util.regex.Matcher keyMatcher = java.util.regex.Pattern.compile("(?ms)^\\s*\\[KEY\\].*?\\R(.*?)(?=^\\s*\\[KEY\\]|^\\s*={10,}|\\z)").matcher(output);
        int index = 1;
        while (keyMatcher.find()) {
            java.util.List<Map<String, Object>> models = new java.util.ArrayList<>();
            java.util.regex.Matcher modelMatcher = java.util.regex.Pattern.compile("(?m)^\\s*(.+?)\\s+输入:\\s*([0-9,]+)\\s+输出:\\s*([0-9,]+)\\s+请求:\\s*([0-9,]+)\\s+¥([0-9.]+)").matcher(keyMatcher.group(1));
            while (modelMatcher.find()) {
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("model", modelMatcher.group(1).trim());
                model.put("inputTokens", modelMatcher.group(2).replace(",", ""));
                model.put("outputTokens", modelMatcher.group(3).replace(",", ""));
                model.put("requests", modelMatcher.group(4).replace(",", ""));
                model.put("cost", modelMatcher.group(5));
                models.add(model);
            }
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("label", "api-key-" + index++);
            key.put("models", models);
            keys.add(key);
        }
        return keys;
    }
    private StateDomainResponse unavailable(String code, String message) { return StateDomainResponse.builder().status(StateStatus.UNAVAILABLE).source("deepseek-platform-api-export")
            .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).build()).data(Map.of()).errorCode(code).errorMessage(message).build(); }
}
