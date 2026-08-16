package com.familyledger.state;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FamilyStateRefreshServiceTest {

    @Test
    void refreshesEveryCollectorAndStoresEachDomainSnapshot() {
        StateCollector finance = collector("finance", fresh("ledger-service:monthlyStats"));
        StateCollector codex = collector("codex", fresh("codex-usage-monitor"));
        StateSnapshotService snapshots = mock(StateSnapshotService.class);

        new FamilyStateRefreshService(List.of(finance, codex), snapshots).refresh();

        verify(snapshots).store("finance", finance.collect());
        verify(snapshots).store("codex", codex.collect());
        verifyNoMoreInteractions(snapshots);
    }

    @Test
    void isolatesCollectorFailureAndRefreshesOtherDomains() {
        StateCollector failed = mock(StateCollector.class);
        when(failed.domain()).thenReturn("deepseek");
        when(failed.collect()).thenThrow(new IllegalStateException("source offline"));
        StateCollector finance = collector("finance", fresh("ledger-service:monthlyStats"));
        StateSnapshotService snapshots = mock(StateSnapshotService.class);

        new FamilyStateRefreshService(List.of(failed, finance), snapshots).refresh();

        verify(snapshots).store(eq("deepseek"), org.mockito.ArgumentMatchers.argThat(response ->
                response.getStatus() == StateStatus.UNAVAILABLE
                        && "STATE_COLLECTOR_FAILED".equals(response.getErrorCode())
                        && response.getData().isEmpty()));
        verify(snapshots).store("finance", finance.collect());
    }

    private static StateCollector collector(String domain, StateDomainResponse response) {
        StateCollector collector = mock(StateCollector.class);
        when(collector.domain()).thenReturn(domain);
        when(collector.collect()).thenReturn(response);
        return collector;
    }

    private static StateDomainResponse fresh(String source) {
        LocalDateTime observed = LocalDateTime.of(2026, 8, 16, 20, 7);
        return StateDomainResponse.builder()
                .status(StateStatus.FRESH)
                .source(source)
                .observedAt(observed)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(300).expiresAt(observed.plusMinutes(5)).build())
                .data(Map.of("verified", true))
                .nextRefreshAt(observed.plusMinutes(5))
                .build();
    }
}
