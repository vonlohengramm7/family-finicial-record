package com.familyledger.state;

import com.familyledger.service.TransactionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinanceStateCollector implements StateCollector {
    private static final long TTL_SECONDS = 900;
    private final TransactionService transactionService;

    public FinanceStateCollector(TransactionService transactionService) { this.transactionService = transactionService; }
    @Override public String domain() { return "finance"; }

    @Override
    public StateDomainResponse collect() {
        try {
            LocalDate now = LocalDate.now();
            List<Map<String, Object>> monthly = transactionService.monthlyStats(null, now.withDayOfMonth(1), now);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("month", now.toString().substring(0, 7));
            data.put("monthlyStats", monthly);
            LocalDateTime observed = LocalDateTime.now();
            LocalDateTime expires = observed.plusSeconds(TTL_SECONDS);
            return StateDomainResponse.builder().status(StateStatus.FRESH).source("ledger-service:monthlyStats")
                    .observedAt(observed).freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).expiresAt(expires).build())
                    .data(data).nextRefreshAt(expires).build();
        } catch (Exception exception) {
            return unavailable("FINANCE_SOURCE_UNAVAILABLE", "账本统计暂时不可读取");
        }
    }
    private StateDomainResponse unavailable(String code, String message) {
        return StateDomainResponse.builder().status(StateStatus.UNAVAILABLE).source("ledger-service:monthlyStats")
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(TTL_SECONDS).build()).data(Map.of())
                .errorCode(code).errorMessage(message).build();
    }
}
