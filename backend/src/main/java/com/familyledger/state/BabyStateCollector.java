package com.familyledger.state;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class BabyStateCollector implements StateCollector {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final long TTL_SECONDS = 86_400;

    private final Path dailyLogDirectory;
    private final Clock clock;

    @Autowired
    public BabyStateCollector(@Value("${family-state.baby.daily-log-directory:/home/vonlohengramm/records/baby-daily-reports/daily-logs}") String dailyLogDirectory) {
        this(Path.of(dailyLogDirectory), Clock.system(BEIJING));
    }

    BabyStateCollector(Path dailyLogDirectory, Clock clock) {
        this.dailyLogDirectory = dailyLogDirectory;
        this.clock = clock;
    }

    @Override
    public String domain() {
        return "baby";
    }

    @Override
    public StateDomainResponse collect() {
        LocalDate today = LocalDate.now(clock);
        Path todayReport = dailyLogDirectory.resolve(today + ".md");
        try {
            Path report = Files.exists(todayReport) ? todayReport : latestReport();
            if (report == null) {
                return unavailable("BABY_REPORT_MISSING", "未找到可读取的育儿日报");
            }
            LocalDate reportDate = LocalDate.parse(report.getFileName().toString().replace(".md", ""));
            String markdown = Files.readString(report);
            LocalDateTime observedAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(report).toInstant(), BEIJING);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reportDate", reportDate.toString());
            data.put("todaySummary", section(markdown, "当日评估", "辅食记录", "喂养记录"));
            data.put("todos", todos(markdown));
            data.put("reportComplete", !markdown.contains("未完日"));
            boolean isToday = reportDate.equals(today);
            return response(isToday ? StateStatus.FRESH : StateStatus.STALE,
                    "baby-daily-report:" + report.getFileName(), observedAt, data,
                    isToday ? null : "TODAY_REPORT_MISSING",
                    isToday ? null : "当天日报缺失，正在展示最近日报");
        } catch (Exception exception) {
            return unavailable("BABY_REPORT_PARSE_FAILED", "育儿日报无法解析");
        }
    }

    private Path latestReport() throws IOException {
        if (!Files.isDirectory(dailyLogDirectory)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(dailyLogDirectory)) {
            return paths.filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.md"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
        }
    }

    private List<String> todos(String markdown) {
        return markdown.lines()
                .filter(line -> line.matches("\\s*-\\s*\\[[ xX]\\].+"))
                .map(line -> line.replaceFirst("^\\s*-\\s*\\[[ xX]\\]\\s*", "").trim())
                .toList();
    }

    private String section(String markdown, String... headings) {
        for (String heading : headings) {
            String marker = "## " + heading;
            int start = markdown.indexOf(marker);
            if (start < 0) {
                continue;
            }
            int end = markdown.indexOf("\n## ", start + marker.length());
            String content = markdown.substring(start + marker.length(), end < 0 ? markdown.length() : end).trim();
            return content.length() > 1_000 ? content.substring(0, 1_000) : content;
        }
        return "";
    }

    private StateDomainResponse unavailable(String code, String message) {
        return response(StateStatus.UNAVAILABLE, "baby-daily-report", null, Map.of(), code, message);
    }

    private StateDomainResponse response(StateStatus status, String source, LocalDateTime observedAt,
                                         Map<String, Object> data, String errorCode, String errorMessage) {
        LocalDateTime expiresAt = observedAt == null ? null : observedAt.plusSeconds(TTL_SECONDS);
        return StateDomainResponse.builder().status(status).source(source).observedAt(observedAt)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).expiresAt(expiresAt).build())
                .data(data).errorCode(errorCode).errorMessage(errorMessage).nextRefreshAt(expiresAt).build();
    }
}
