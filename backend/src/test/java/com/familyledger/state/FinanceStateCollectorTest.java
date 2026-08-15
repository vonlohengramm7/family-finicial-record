package com.familyledger.state;

import com.familyledger.entity.AutoTransaction;
import com.familyledger.entity.Transaction;
import com.familyledger.service.AutoTransactionService;
import com.familyledger.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 财务状态聚合规则（t_5d7706ee）：
 * 从 family_ledger（只读服务聚合）与资产/自动交易源推导 cash_balance、
 * portfolio_value、recent_tx_health、auto_trade 子快照。
 * 每个子快照携带独立 source/observedAt/freshUntil/status；
 * 单一子源失败只置该子快照 unavailable+error，绝不伪造余额、不拖垮整域。
 */
class FinanceStateCollectorTest {

    @TempDir
    Path tempDir;

    private TransactionService txService = mock(TransactionService.class);
    private AutoTransactionService autoService = mock(AutoTransactionService.class);

    private FinanceStateCollector collector(Path assetFile) {
        return new FinanceStateCollector(txService, autoService, assetFile);
    }

    // ---------- cash_balance ----------

    @Test
    void cashBalanceAggregatesCurrentMonthAndCumulativeNetFromLedger() throws Exception {
        Map<String, Object> july = row("2026-07", "1000.00", "4000.00", "-3000.00");
        Map<String, Object> august = row("2026-08", "13605.69", "20000.00", "-6394.31");
        when(txService.monthlyStats(isNull(), isNull(), isNull())).thenReturn(List.of(july, august));
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> cash = (Map<String, Object>) response.getData().get("cashBalance");
        assertThat(cash).isNotNull();
        assertThat(cash.get("status")).isEqualTo(StateStatus.FRESH.name());
        assertThat(cash.get("source")).isEqualTo("ledger-service:monthlyStats");
        assertThat(cash.get("observedAt")).isNotNull();
        assertThat(cash.get("freshUntil")).isNotNull();
        assertThat(cash.get("error")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) cash.get("value");
        assertThat(value.get("month")).isEqualTo("2026-08");
        assertThat(new BigDecimal(value.get("income").toString())).isEqualByComparingTo("20000.00");
        assertThat(new BigDecimal(value.get("expense").toString())).isEqualByComparingTo("-6394.31");
        assertThat(new BigDecimal(value.get("net").toString())).isEqualByComparingTo("13605.69");
        // 累计净额 = 全部月份 total 之和
        assertThat(new BigDecimal(value.get("cumulativeNet").toString())).isEqualByComparingTo("14605.69");
    }

    @Test
    void cashBalanceLedgerFailureMarksUnavailableWithReasonNeverFabricatesZero() throws Exception {
        when(txService.monthlyStats(isNull(), isNull(), isNull())).thenThrow(new RuntimeException("db down"));
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> cash = (Map<String, Object>) response.getData().get("cashBalance");
        assertThat(cash.get("status")).isEqualTo(StateStatus.UNAVAILABLE.name());
        assertThat(((Map<String, Object>) cash.get("error")).get("code")).isEqualTo("FINANCE_SOURCE_UNAVAILABLE");
        assertThat(((Map<String, Object>) cash.get("error")).get("reason").toString()).isNotBlank();
        // 失败时不得出现任何 0 值余额
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) cash.get("value");
        assertThat(value).isEmpty();
    }

    // ---------- portfolio_value（资产文件） ----------

    @Test
    void portfolioValueParsesAssetFileByOwnerAndTotal() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(txService.countUnsettled()).thenReturn(0L);
        when(autoService.listActive()).thenReturn(List.of());
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> portfolio = (Map<String, Object>) response.getData().get("portfolioValue");
        assertThat(portfolio.get("status")).isEqualTo(StateStatus.FRESH.name());
        assertThat(portfolio.get("source")).isEqualTo("family-assets:家庭资产.md");
        assertThat(portfolio.get("observedAt")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) portfolio.get("value");
        assertThat(new BigDecimal(value.get("total").toString())).isEqualByComparingTo("476758.23");
        assertThat(value.get("asOf")).isEqualTo("2026-07-28");
        @SuppressWarnings("unchecked")
        Map<String, Object> byOwner = (Map<String, Object>) value.get("byOwner");
        assertThat(new BigDecimal(byOwner.get("爸爸").toString())).isEqualByComparingTo("210594.03");
        assertThat(new BigDecimal(byOwner.get("妈妈").toString())).isEqualByComparingTo("145408.20");
        assertThat(new BigDecimal(byOwner.get("家庭共有").toString())).isEqualByComparingTo("120756");
    }

    @Test
    void portfolioValueUnavailableWhenAssetFileMissing() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(txService.countUnsettled()).thenReturn(0L);
        when(autoService.listActive()).thenReturn(List.of());
        Path missing = tempDir.resolve("不存在.md");

        StateDomainResponse response = collector(missing).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> portfolio = (Map<String, Object>) response.getData().get("portfolioValue");
        assertThat(portfolio.get("status")).isEqualTo(StateStatus.UNAVAILABLE.name());
        assertThat(((Map<String, Object>) portfolio.get("error")).get("code")).isEqualTo("ASSET_SOURCE_UNAVAILABLE");
        // 缺失时不得出现 0 元资产
        assertThat(((Map<String, Object>) portfolio.get("value"))).isEmpty();
        // 其他子快照不受影响（域内隔离）
        @SuppressWarnings("unchecked")
        Map<String, Object> cash = (Map<String, Object>) response.getData().get("cashBalance");
        assertThat(cash.get("status")).isEqualTo(StateStatus.FRESH.name());
    }

    @Test
    void portfolioValueUnavailableWhenAssetFileUnparseable() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(txService.countUnsettled()).thenReturn(0L);
        when(autoService.listActive()).thenReturn(List.of());
        Path garbage = Files.writeString(tempDir.resolve("坏文件.md"), "这不是资产文件\n没有表格\n");

        StateDomainResponse response = collector(garbage).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> portfolio = (Map<String, Object>) response.getData().get("portfolioValue");
        assertThat(portfolio.get("status")).isEqualTo(StateStatus.UNAVAILABLE.name());
        assertThat(((Map<String, Object>) portfolio.get("error")).get("code")).isEqualTo("ASSET_PARSE_FAILED");
        assertThat(((Map<String, Object>) portfolio.get("value"))).isEmpty();
    }

    // ---------- recent_tx_health ----------

    @Test
    void recentTxHealthAggregatesLastTxAndCountsFromLedger() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        Transaction latest = new Transaction();
        latest.setTransDate(LocalDate.of(2026, 8, 15));
        when(txService.list(eq(1), eq(1), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(latest));
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(42L);
        when(txService.countUnsettled()).thenReturn(2L);
        when(autoService.listActive()).thenReturn(List.of());
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) response.getData().get("recentTxHealth");
        assertThat(health.get("status")).isEqualTo(StateStatus.FRESH.name());
        assertThat(health.get("source")).isEqualTo("ledger-service:transactions");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) health.get("value");
        assertThat(value.get("lastTxDate")).isEqualTo("2026-08-15");
        assertThat(value.get("last7dCount")).isEqualTo(42L);
        assertThat(value.get("unsettledCount")).isEqualTo(2L);
    }

    @Test
    void recentTxHealthFailureIsolatedDoesNotBreakCashBalance() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("list down"));
        when(autoService.listActive()).thenReturn(List.of());
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) response.getData().get("recentTxHealth");
        assertThat(health.get("status")).isEqualTo(StateStatus.UNAVAILABLE.name());
        assertThat(((Map<String, Object>) health.get("error")).get("code")).isEqualTo("FINANCE_SOURCE_UNAVAILABLE");
        @SuppressWarnings("unchecked")
        Map<String, Object> cash = (Map<String, Object>) response.getData().get("cashBalance");
        assertThat(cash.get("status")).isEqualTo(StateStatus.FRESH.name());
    }

    // ---------- auto_trade（自动交易源） ----------

    @Test
    void autoTradeAggregatesActiveRecurringTransactions() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(txService.countUnsettled()).thenReturn(0L);
        AutoTransaction a1 = auto(1L, "商贷月供", LocalDate.of(2026, 9, 10));
        AutoTransaction a2 = auto(2L, "公积金贷款月供", LocalDate.of(2026, 8, 29));
        AutoTransaction inactive = auto(3L, "车位租金", LocalDate.of(2027, 2, 25));
        inactive.setIsActive(false);
        when(autoService.listActive()).thenReturn(List.of(a1, a2));
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> autoTrade = (Map<String, Object>) response.getData().get("autoTrade");
        assertThat(autoTrade.get("status")).isEqualTo(StateStatus.FRESH.name());
        assertThat(autoTrade.get("source")).isEqualTo("ledger-service:auto-transactions");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) autoTrade.get("value");
        assertThat(value.get("activeCount")).isEqualTo(2);
        assertThat(value.get("nextRunDate")).isEqualTo("2026-08-29");
    }

    @Test
    void autoTradeFailureIsolatedDoesNotBreakDomain() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(txService.countUnsettled()).thenReturn(0L);
        when(autoService.listActive()).thenThrow(new RuntimeException("auto table down"));
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        @SuppressWarnings("unchecked")
        Map<String, Object> autoTrade = (Map<String, Object>) response.getData().get("autoTrade");
        assertThat(autoTrade.get("status")).isEqualTo(StateStatus.UNAVAILABLE.name());
        assertThat(((Map<String, Object>) autoTrade.get("error")).get("code")).isEqualTo("AUTO_TRADE_SOURCE_UNAVAILABLE");
        @SuppressWarnings("unchecked")
        Map<String, Object> cash = (Map<String, Object>) response.getData().get("cashBalance");
        assertThat(cash.get("status")).isEqualTo(StateStatus.FRESH.name());
    }

    // ---------- 域级状态 ----------

    @Test
    void domainUnavailableWhenAllSubSnapshotsFail() {
        when(txService.monthlyStats(any(), any(), any())).thenThrow(new RuntimeException("db down"));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));
        when(autoService.listActive()).thenThrow(new RuntimeException("db down"));
        Path missing = tempDir.resolve("不存在.md");

        StateDomainResponse response = collector(missing).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(response.getErrorCode()).isEqualTo("FINANCE_SOURCE_UNAVAILABLE");
        assertThat(response.getErrorMessage()).isNotBlank();
        assertThat(response.getData().get("cashBalance")).isNotNull();
    }

    @Test
    void domainFreshWhenPrimaryLedgerAggregationSucceeds() throws Exception {
        when(txService.monthlyStats(any(), any(), any())).thenReturn(List.of(row("2026-08", "100", "100", "0")));
        when(txService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txService.count(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(txService.countUnsettled()).thenReturn(0L);
        when(autoService.listActive()).thenReturn(List.of());
        Path asset = Files.writeString(tempDir.resolve("家庭资产.md"), assetFixture());

        StateDomainResponse response = collector(asset).collect();

        assertThat(response.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(response.getSource()).isEqualTo("ledger-service:monthlyStats");
        assertThat(response.getObservedAt()).isNotNull();
        assertThat(response.getFreshness().getExpiresAt()).isNotNull();
    }

    // ---------- fixtures ----------

    private static Map<String, Object> row(String month, String total, String income, String expense) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", month);
        m.put("total", new BigDecimal(total));
        m.put("income", new BigDecimal(income));
        m.put("expense", new BigDecimal(expense));
        return m;
    }

    private static AutoTransaction auto(Long id, String note, LocalDate nextRunDate) {
        AutoTransaction t = new AutoTransaction();
        t.setId(id);
        t.setNote(note);
        t.setNextRunDate(nextRunDate);
        t.setIsActive(true);
        return t;
    }

    private static String assetFixture() {
        return """
                # 💰 家庭资产概况（截至2026年7月28日）

                ## 👨 爸爸（张栩）
                | 账户 | 金额 |
                |:---|:---:|
                | 债基 | 108,000 元 |
                | 余额宝 | 17,000 元 |
                | 招行 | 58,594.03 元 |
                | 个人养老金（友邦利享年年） | 12,000 元 🆕 |
                | 国盛通股票 | 15,000 元 |
                | **小计** | **210,594.03 元** |

                ## 👩 妈妈（齐久莹）
                | 项目 | 金额 |
                |:---|:---:|
                | 📈 沪深300（国泰海通018258） | 18,000 元 |
                | 📈 创业板指数 | 6,000 元 |
                | 🪙 黄金 | 10,000 元 |
                | 💰 基金（支付宝等） | 110,000 元 |
                | 🏦 活期余额 | 1,408.20 元 |
                | **小计** | **145,408.20 元** |

                ## 👨‍👩‍👧 家庭共有
                | 账户 | 金额 |
                |:---|:---:|
                | 建行（含90k理财） | 90,756 元 |
                | 🍼 汤圆存单 | 30,000 元 |
                | **小计** | **120,756 元** |

                > **最后更新：2026-08-15**
                """;
    }
}
