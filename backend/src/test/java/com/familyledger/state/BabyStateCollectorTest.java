package com.familyledger.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BabyStateCollectorTest {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T04:00:00Z"), BEIJING);

    @TempDir
    Path tempDir;

    @Test
    void usesTodayReportAndExposesOnlyStructuredSummary() throws Exception {
        Files.writeString(tempDir.resolve("2026-08-15.md"), "# 今日日报\n\n## 喂养记录\n- 奶量 750ml\n\n## 近期计划\n- [ ] 观察米粉耐受\n");

        BabyStateCollector collector = new BabyStateCollector(tempDir, CLOCK);

        StateDomainResponse response = collector.collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getSource()).isEqualTo("baby-daily-report:2026-08-15.md");
        assertThat(response.getData()).containsEntry("reportDate", "2026-08-15");
        assertThat(response.getData()).containsKey("todaySummary");
        assertThat(response.getData()).doesNotContainKey("rawMarkdown");
        assertThat(response.getData().get("todos").toString()).contains("观察米粉耐受");
    }

    @Test
    void fallsBackToLatestReportAsStaleWhenTodayIsMissing() throws Exception {
        Files.writeString(tempDir.resolve("2026-08-14.md"), "# 昨日日报\n\n## 近期计划\n- [ ] 复测体重\n");

        StateDomainResponse response = new BabyStateCollector(tempDir, CLOCK).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.STALE);
        assertThat(response.getData()).containsEntry("reportDate", "2026-08-14");
        assertThat(response.getErrorCode()).isEqualTo("TODAY_REPORT_MISSING");
    }

    @Test
    void parsesRealReportWithEmojiPrefixedHeadings() throws Exception {
        // 真实日报 2026-08-15.md：标题带 emoji 前缀（## ⚠️ 当日评估 / ## 📅 近期计划）
        String realReport = """
                # 📋 8月15日 · 汤圆育儿日报（截至10:30，未完日）

                ## 🥣 辅食记录

                | 时间 | 食物 | 调配 | 实际表现 |
                |:---:|:---|:---|:---|
                | 10:30 | 加铁米粉（首次） | 5g米粉 + 60ml水 | 吃了4勺，接受度好、愿意尝试 |

                ## ⚠️ 当日评估

                - **辅食接受：✅** 第一次尝试加铁米粉就愿意吃4勺

                ## 📅 近期计划

                - 连续2—3天观察同一种加铁米粉的接受度与排便变化后，再考虑按耐受情况扩展食材。
                - 待当日奶量、排泄和护理补齐后，回填本日报并更新全天评估。
                """;
        Files.writeString(tempDir.resolve("2026-08-15.md"), realReport);

        StateDomainResponse response = new BabyStateCollector(tempDir, CLOCK).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getData().get("todaySummary").toString())
                .contains("辅食接受", "愿意吃4勺");
        assertThat(response.getData()).containsKey("todos");
    }

    @Test
    void collectsTrendsFromHealthRecordAndWeightTimeline() throws Exception {
        Files.writeString(tempDir.resolve("2026-08-15.md"), "# 今日日报\n\n## 当日评估\n- 正常\n");
        Files.writeString(tempDir.resolve("health-record.md"), """
                # 汤圆健康记录总览

                ## 发育里程碑
                - 167天（8/7）：自主腹爬 🎊
                - 174天（8/14起）：频繁用双腿蹬起，腹部短暂离地 🎊

                ## 疫苗进度
                | 疫苗 | 针次 | 日期 | 状态 |
                |------|:---:|:----:|:----:|
                | 乙肝 | #3 | 约8/21（6月龄） | 📅 |
                | 手足口(EV71) | 共2针 | 约8月下旬起 | 📅 |
                """);
        Files.writeString(tempDir.resolve("weight-timeline.md"), """
                # 汤圆体重记录

                | 日期 | 体重(g) | 日龄 | 变化 | 备注 |
                |------|---------|------|------|------|
                | 8/4 | 7000 | 164 | -100 | 较7/29少100g |
                | 8/10 | 7100 | 170 | +100 | 全奶粉580ml/4次 |
                """);

        StateDomainResponse response = new BabyStateCollector(tempDir, CLOCK).collect();

        assertThat(response.getData().get("trends").toString())
                .contains("7100", "8/10")
                .contains("自主腹爬", "蹬起");
        // 疫苗计划是显式待办
        assertThat(response.getData().get("todos").toString())
                .contains("乙肝", "手足口");
    }

    @Test
    void trendSourceFailureDoesNotBlockDailySummary() throws Exception {
        // 只有日报，健康文件缺失 → 今日卡片仍然可用
        Files.writeString(tempDir.resolve("2026-08-15.md"), "# 今日日报\n\n## 当日评估\n- 正常\n");

        StateDomainResponse response = new BabyStateCollector(tempDir, CLOCK).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getData()).containsKey("todaySummary");
    }
}
