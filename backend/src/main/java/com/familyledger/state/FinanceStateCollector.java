package com.familyledger.state;

import com.familyledger.entity.AutoTransaction;
import com.familyledger.entity.Transaction;
import com.familyledger.service.AutoTransactionService;
import com.familyledger.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务状态聚合器（t_5d7706ee）。
 *
 * 只从既有 family_ledger（TransactionService 只读聚合、AutoTransactionService 只读查询）
 * 与家庭资产文件（~/records/财产/家庭资产.md）推导财务状态，不迁移/复制/创建任何交易。
 *
 * data 内含四个子快照，各自携带 source/observedAt/freshUntil/status 与独立错误：
 *  - cashBalance      ← ledger-service:monthlyStats（当月收支净额 + 累计净额）
 *  - portfolioValue   ← family-assets:家庭资产.md（按人小计与合计，文件 mtime 作为观测时间）
 *  - recentTxHealth   ← ledger-service:transactions（最近交易日期 / 近7日笔数 / 未结算笔数）
 *  - autoTrade        ← ledger-service:auto-transactions（生效中的周期性扣款概览）
 *
 * 失败隔离：单一子源失败只置该子快照 UNAVAILABLE+error，绝不伪造余额；
 * 仅当全部子快照均不可用时域状态才为 UNAVAILABLE。
 */
@Component
public class FinanceStateCollector implements StateCollector {
    private static final long LEDGER_TTL_SECONDS = 900;
    private static final long FILE_TTL_SECONDS = 86_400;
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String LEDGER_SOURCE = "ledger-service:monthlyStats";
    private static final String ASSET_SOURCE = "family-assets:家庭资产.md";

    private final TransactionService transactionService;
    private final AutoTransactionService autoTransactionService;
    private final Path assetFile;

    @Autowired
    public FinanceStateCollector(TransactionService transactionService,
                                 AutoTransactionService autoTransactionService,
                                 @Value("${family-state.finance.asset-file:/home/vonlohengramm/records/财产/家庭资产.md}") String assetFile) {
        this(transactionService, autoTransactionService, Path.of(assetFile));
    }

    FinanceStateCollector(TransactionService transactionService, AutoTransactionService autoTransactionService, Path assetFile) {
        this.transactionService = transactionService;
        this.autoTransactionService = autoTransactionService;
        this.assetFile = assetFile;
    }

    @Override
    public String domain() {
        return "finance";
    }

    @Override
    public StateDomainResponse collect() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> months = monthlyStatsOrEmpty();
        Map<String, Object> cashBalance = cashBalance(months);
        Map<String, Object> portfolioValue = portfolioValue();
        Map<String, Object> recentTxHealth = recentTxHealth();
        Map<String, Object> autoTrade = autoTrade();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("month", now.toString().substring(0, 7));
        data.put("monthlyStats", months);
        data.put("cashBalance", cashBalance);
        data.put("portfolioValue", portfolioValue);
        data.put("recentTxHealth", recentTxHealth);
        data.put("autoTrade", autoTrade);

        boolean anyFresh = isFresh(cashBalance) || isFresh(portfolioValue) || isFresh(recentTxHealth) || isFresh(autoTrade);
        if (!anyFresh) {
            return StateDomainResponse.builder().status(StateStatus.UNAVAILABLE).source(LEDGER_SOURCE)
                    .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(LEDGER_TTL_SECONDS).build())
                    .data(data).errorCode("FINANCE_SOURCE_UNAVAILABLE").errorMessage("账本与资产聚合均不可用").build();
        }
        LocalDateTime expires = now.plusSeconds(LEDGER_TTL_SECONDS);
        return StateDomainResponse.builder().status(StateStatus.FRESH).source(LEDGER_SOURCE).observedAt(now)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(LEDGER_TTL_SECONDS).expiresAt(expires).build())
                .data(data).nextRefreshAt(expires).build();
    }

    // ---------- cash_balance：当月收支净额 + 累计净额（全部来自账本只读聚合） ----------

    private List<Map<String, Object>> monthlyStatsOrEmpty() {
        try {
            List<Map<String, Object>> months = transactionService.monthlyStats(null, null, null);
            return months == null ? List.of() : months;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, Object> cashBalance(List<Map<String, Object>> months) {
        if (months.isEmpty()) {
            return sub(LEDGER_SOURCE, null, null, "FINANCE_SOURCE_UNAVAILABLE", "账本暂无交易可聚合", Map.of());
        }
        Map<String, Object> current = months.get(months.size() - 1);
        BigDecimal cumulative = months.stream()
                .map(m -> (BigDecimal) m.get("total"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("month", current.get("month"));
        value.put("income", current.get("income"));
        value.put("expense", current.get("expense"));
        value.put("net", current.get("total"));
        value.put("cumulativeNet", cumulative);
        return sub(LEDGER_SOURCE, LocalDateTime.now(), LEDGER_TTL_SECONDS, null, null, value);
    }

    // ---------- portfolio_value：家庭资产文件（只读解析，缺失/坏文件 → unavailable） ----------

    private Map<String, Object> portfolioValue() {
        if (!Files.isRegularFile(assetFile)) {
            return sub(ASSET_SOURCE, null, null, "ASSET_SOURCE_UNAVAILABLE", "家庭资产文件不可读取", Map.of());
        }
        try {
            Map<String, Object> value = FamilyAssetParser.parse(assetFile);
            LocalDateTime observed = LocalDateTime.ofInstant(Files.getLastModifiedTime(assetFile).toInstant(), BEIJING);
            return sub(ASSET_SOURCE, observed, FILE_TTL_SECONDS, null, null, value);
        } catch (IOException | FamilyAssetParser.AssetParseException exception) {
            return sub(ASSET_SOURCE, null, null, "ASSET_PARSE_FAILED", "家庭资产文件无法解析", Map.of());
        }
    }

    // ---------- recent_tx_health：账本近期交易健康度（只读） ----------

    private Map<String, Object> recentTxHealth() {
        try {
            LocalDate today = LocalDate.now();
            List<Transaction> latestPage = transactionService.list(1, 1, null, null, null, null, null, null, null, null);
            String lastTxDate = (latestPage == null || latestPage.isEmpty() || latestPage.get(0).getTransDate() == null)
                    ? null : latestPage.get(0).getTransDate().toString();
            long last7dCount = transactionService.count(null, null, null, today.minusDays(7), today, null, null, null);
            long unsettledCount = transactionService.countUnsettled();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("lastTxDate", lastTxDate);
            value.put("last7dCount", last7dCount);
            value.put("unsettledCount", unsettledCount);
            return sub("ledger-service:transactions", LocalDateTime.now(), LEDGER_TTL_SECONDS, null, null, value);
        } catch (Exception exception) {
            return sub("ledger-service:transactions", null, null, "FINANCE_SOURCE_UNAVAILABLE", "账本交易健康度不可读取", Map.of());
        }
    }

    // ---------- auto_trade：生效中的周期性交易（只读，绝不写入 auto_transaction） ----------

    private Map<String, Object> autoTrade() {
        try {
            List<AutoTransaction> active = autoTransactionService.listActive();
            LocalDate nextRunDate = active.stream()
                    .map(AutoTransaction::getNextRunDate)
                    .filter(date -> date != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("activeCount", active.size());
            value.put("nextRunDate", nextRunDate == null ? null : nextRunDate.toString());
            return sub("ledger-service:auto-transactions", LocalDateTime.now(), FILE_TTL_SECONDS, null, null, value);
        } catch (Exception exception) {
            return sub("ledger-service:auto-transactions", null, null, "AUTO_TRADE_SOURCE_UNAVAILABLE", "自动交易概览不可读取", Map.of());
        }
    }

    // ---------- 子快照构造 ----------

    private static Map<String, Object> sub(String source, LocalDateTime observedAt, Long ttlSeconds,
                                           String errorCode, String errorMessage, Map<String, Object> value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (errorCode == null) {
            snapshot.put("status", StateStatus.FRESH.name());
            snapshot.put("observedAt", observedAt.toString());
            snapshot.put("freshUntil", observedAt.plusSeconds(ttlSeconds).toString());
            snapshot.put("error", null);
        } else {
            snapshot.put("status", StateStatus.UNAVAILABLE.name());
            snapshot.put("observedAt", null);
            snapshot.put("freshUntil", null);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", errorCode);
            error.put("reason", errorMessage);
            snapshot.put("error", error);
        }
        snapshot.put("source", source);
        snapshot.put("value", value == null ? Map.of() : value);
        return snapshot;
    }

    private static boolean isFresh(Map<String, Object> snapshot) {
        return StateStatus.FRESH.name().equals(snapshot.get("status"));
    }
}
