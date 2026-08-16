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
import java.util.ArrayList;
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
    private final Path healthRecord;
    private final Path weightTimeline;
    private final Clock clock;

    @Autowired
    public BabyStateCollector(
            @Value("${family-state.baby.daily-log-directory:/home/vonlohengramm/records/baby-daily-reports/daily-logs}") String dailyLogDirectory,
            @Value("${family-state.baby.health-record:/home/vonlohengramm/records/汤圆/health/health-record.md}") String healthRecord,
            @Value("${family-state.baby.weight-timeline:/home/vonlohengramm/records/汤圆/health/weight-timeline.md}") String weightTimeline) {
        this(Path.of(dailyLogDirectory), Path.of(healthRecord), Path.of(weightTimeline), Clock.system(BEIJING));
    }

    BabyStateCollector(Path dailyLogDirectory, Clock clock) {
        this(dailyLogDirectory, dailyLogDirectory.resolve("health-record.md"), dailyLogDirectory.resolve("weight-timeline.md"), clock);
    }

    BabyStateCollector(Path dailyLogDirectory, Path healthRecord, Path weightTimeline, Clock clock) {
        this.dailyLogDirectory = dailyLogDirectory;
        this.healthRecord = healthRecord;
        this.weightTimeline = weightTimeline;
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
        Map<String, Object> data = new LinkedHashMap<>();
        String errorCode = null;
        String errorMessage = null;
        LocalDateTime observedAt = null;
        StateStatus status;
        String source;
        try {
            Path report = Files.exists(todayReport) ? todayReport : latestReport();
            if (report == null) {
                return unavailable("BABY_REPORT_MISSING", "未找到可读取的育儿日报");
            }
            LocalDate reportDate = LocalDate.parse(report.getFileName().toString().replace(".md", ""));
            String markdown = Files.readString(report);
            observedAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(report).toInstant(), BEIJING);
            data.put("reportDate", reportDate.toString());
            data.put("todaySummary", section(markdown, "当日评估", "辅食记录", "喂养记录"));
            List<String> todos = new ArrayList<>(todos(markdown));
            todos.addAll(vaccineTodos());
            data.put("todos", todos);
            data.put("reportComplete", !markdown.contains("未完日"));
            boolean isToday = reportDate.equals(today);
            status = isToday ? StateStatus.FRESH : StateStatus.STALE;
            source = "baby-daily-report:" + report.getFileName();
            if (!isToday) {
                errorCode = "TODAY_REPORT_MISSING";
                errorMessage = "当天日报缺失，正在展示最近日报";
            }
        } catch (Exception exception) {
            return unavailable("BABY_REPORT_PARSE_FAILED", "育儿日报无法解析");
        }
        // 趋势/里程碑独立采集：失败不阻断今日卡片
        data.put("trends", trends());
        return response(status, source, observedAt, data, errorCode, errorMessage);
    }

    private Map<String, Object> trends() {
        Map<String, Object> trends = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(healthRecord)) {
                String markdown = Files.readString(healthRecord);
                LocalDateTime healthObserved = LocalDateTime.ofInstant(Files.getLastModifiedTime(healthRecord).toInstant(), BEIJING);
                trends.put("milestones", milestones(markdown));
                trends.put("healthSource", "health-record.md");
                trends.put("healthObservedAt", healthObserved.toString());
            }
        } catch (IOException ignored) {
            trends.put("healthSource", "health-record.md");
            trends.put("healthError", "unavailable");
        }
        try {
            if (Files.isRegularFile(weightTimeline)) {
                String markdown = Files.readString(weightTimeline);
                LocalDateTime weightObserved = LocalDateTime.ofInstant(Files.getLastModifiedTime(weightTimeline).toInstant(), BEIJING);
                trends.put("weight", latestWeight(markdown));
                trends.put("weightSource", "weight-timeline.md");
                trends.put("weightObservedAt", weightObserved.toString());
            }
        } catch (IOException ignored) {
            trends.put("weightSource", "weight-timeline.md");
            trends.put("weightError", "unavailable");
        }
        return trends;
    }

    private List<String> milestones(String markdown) {
        List<String> lines = markdown.lines()
                .filter(line -> line.trim().startsWith("- ") && line.contains("天"))
                .map(line -> line.trim().replaceFirst("^-\\s*", ""))
                .filter(line -> line.contains("🎊") || line.contains("🎉") || line.contains("🧠") || line.matches(".*\\d+天.*"))
                .toList();
        return lines.size() <= 3 ? lines : lines.subList(lines.size() - 3, lines.size());
    }

    private List<String> vaccineTodos() {
        if (!Files.isRegularFile(healthRecord)) return List.of();
        try {
            String markdown = Files.readString(healthRecord);
            List<String> todos = new ArrayList<>();
            boolean inVaccineTable = false;
            for (String line : markdown.lines().toList()) {
                if (line.startsWith("## ")) {
                    inVaccineTable = line.contains("疫苗");
                    continue;
                }
                if (inVaccineTable && line.contains("📅") && line.startsWith("|")) {
                    String[] cells = line.split("\\|");
                    if (cells.length >= 4) {
                        String name = cells[1].trim();
                        String dose = cells[2].trim();
                        String date = cells[3].trim();
                        todos.add("疫苗：" + name + " " + dose + " " + date);
                    }
                }
            }
            return todos;
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private Map<String, Object> latestWeight(String markdown) {
        Map<String, Object> latest = new LinkedHashMap<>();
        String lastDataRow = null;
        for (String line : markdown.lines().toList()) {
            if (line.startsWith("|") && !line.contains("日期") && !line.matches("\\|\\s*-+.*")) {
                lastDataRow = line;
            }
        }
        if (lastDataRow == null) return latest;
        String[] cells = lastDataRow.split("\\|");
        if (cells.length >= 5) {
            latest.put("date", cells[1].trim());
            latest.put("weightG", cells[2].trim());
            latest.put("ageDays", cells[3].trim());
            latest.put("change", cells[4].trim());
            if (cells.length >= 6 && !cells[5].isBlank()) latest.put("note", cells[5].trim());
        }
        return latest;
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
        List<String> lines = markdown.lines().toList();
        for (String heading : headings) {
            int start = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("## ") && stripEmoji(lines.get(i).substring(3)).contains(heading)) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                continue;
            }
            StringBuilder content = new StringBuilder();
            for (int i = start + 1; i < lines.size() && !lines.get(i).startsWith("## "); i++) {
                if (!lines.get(i).isBlank()) {
                    content.append(lines.get(i).trim()).append('\n');
                }
            }
            String result = content.toString().trim();
            return result.length() > 1_000 ? result.substring(0, 1_000) : result;
        }
        return "";
    }

    private String stripEmoji(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}]", "");
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
