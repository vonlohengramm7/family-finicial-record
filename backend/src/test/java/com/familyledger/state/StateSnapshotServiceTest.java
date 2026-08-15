package com.familyledger.state;

import com.familyledger.entity.StateSnapshot;
import com.familyledger.mapper.StateSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateSnapshotServiceTest {

    private static StateDomainResponse fresh(String source, Map<String, Object> data) {
        LocalDateTime observed = LocalDateTime.now().minusMinutes(5);
        LocalDateTime expires = LocalDateTime.now().plusHours(1);
        return StateDomainResponse.builder()
                .status(StateStatus.FRESH).source(source).observedAt(observed)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(3600).expiresAt(expires).build())
                .data(data).nextRefreshAt(expires).build();
    }

    @Test
    void preservesLastSuccessfulPayloadAsStaleWhenRefreshFails() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        StateSnapshot previous = new StateSnapshot();
        previous.setDomain("codex");
        previous.setSnapshotKey("overview");
        previous.setPayloadJson("{\"weeklyUsedPercent\":11}");
        previous.setSource("codex-usage-monitor");
        previous.setObservedAt(LocalDateTime.of(2026, 8, 15, 20, 7));
        previous.setFreshUntil(LocalDateTime.of(2026, 8, 15, 21, 7));
        previous.setStatus("FRESH");
        when(mapper.selectOne(any())).thenReturn(previous);

        StateSnapshotService service = new StateSnapshotService(mapper);
        StateDomainResponse failed = StateDomainResponse.builder()
                .status(StateStatus.UNAVAILABLE).source("codex-usage-monitor")
                .data(Map.of()).errorCode("CODEX_SOURCE_UNAVAILABLE").errorMessage("采集源不可读取")
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(3600).build()).build();

        StateDomainResponse result = service.store("codex", failed);

        assertThat(result.getStatus()).isEqualTo(StateStatus.STALE);
        assertThat(result.getData()).containsEntry("weeklyUsedPercent", 11);
        assertThat(result.getObservedAt()).isEqualTo(previous.getObservedAt());
        assertThat(result.getErrorCode()).isEqualTo("CODEX_SOURCE_UNAVAILABLE");
    }

    @Test
    void storePersistsUnderExplicitDomainAndKey() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        StateSnapshotService service = new StateSnapshotService(mapper);

        StateDomainResponse snapshot = fresh("deepseek-platform-api-export", Map.of("todayCost", "0.12"));
        service.store("deepseek", "by_api_key:1", snapshot);

        verify(mapper).insert(org.mockito.ArgumentMatchers.<StateSnapshot>argThat(entity ->
                "deepseek".equals(entity.getDomain()) && "by_api_key:1".equals(entity.getSnapshotKey())
                        && "FRESH".equals(entity.getStatus())
                        && entity.getPayloadJson().contains("todayCost")));
    }

    @Test
    void storeUpsertsExistingDomainKeyInsteadOfDuplicating() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        StateSnapshot previous = new StateSnapshot();
        previous.setDomain("deepseek");
        previous.setSnapshotKey("by_api_key:1");
        previous.setPayloadJson("{\"todayCost\":\"0.10\"}");
        previous.setStatus("FRESH");
        when(mapper.selectOne(any())).thenReturn(previous);
        StateSnapshotService service = new StateSnapshotService(mapper);

        service.store("deepseek", "by_api_key:1", fresh("deepseek-platform-api-export", Map.of("todayCost", "0.22")));

        verify(mapper).updateById(org.mockito.ArgumentMatchers.<StateSnapshot>argThat(entity ->
                "by_api_key:1".equals(entity.getSnapshotKey()) && entity.getPayloadJson().contains("0.22")));
    }

    @Test
    void readLatestByDomainAndKeyReturnsThatSnapshot() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        StateSnapshot row = new StateSnapshot();
        row.setDomain("deepseek");
        row.setSnapshotKey("by_api_key:1");
        row.setPayloadJson("{\"todayCost\":\"0.12\"}");
        row.setSource("deepseek-platform-api-export");
        row.setObservedAt(LocalDateTime.now().minusMinutes(5));
        row.setFreshUntil(LocalDateTime.now().plusHours(1));
        row.setStatus("FRESH");
        when(mapper.selectOne(any())).thenReturn(row);
        StateSnapshotService service = new StateSnapshotService(mapper);

        StateDomainResponse result = service.get("deepseek", "by_api_key:1");

        assertThat(result.getStatus()).isEqualTo(StateStatus.FRESH);
        assertThat(result.getData()).containsEntry("todayCost", "0.12");
        assertThat(result.getSource()).isEqualTo("deepseek-platform-api-export");
    }

    @Test
    void readLatestByUnknownDomainKeyReturnsUnavailable() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        StateSnapshotService service = new StateSnapshotService(mapper);

        StateDomainResponse result = service.get("deepseek", "by_api_key:99");

        assertThat(result.getStatus()).isEqualTo(StateStatus.UNAVAILABLE);
        assertThat(result.getErrorCode()).isEqualTo("SNAPSHOT_MISSING");
    }

    @Test
    void readLatestMarksStaleWhenFreshUntilPassed() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        StateSnapshot row = new StateSnapshot();
        row.setDomain("deepseek");
        row.setSnapshotKey("by_api_key:1");
        row.setPayloadJson("{\"todayCost\":\"0.12\"}");
        row.setSource("deepseek-platform-api-export");
        row.setObservedAt(LocalDateTime.now().minusHours(2));
        row.setFreshUntil(LocalDateTime.now().minusHours(1));
        row.setStatus("FRESH");
        when(mapper.selectOne(any())).thenReturn(row);
        StateSnapshotService service = new StateSnapshotService(mapper);

        StateDomainResponse result = service.get("deepseek", "by_api_key:1");

        assertThat(result.getStatus()).isEqualTo(StateStatus.STALE);
        assertThat(result.getData()).containsEntry("todayCost", "0.12");
    }

    @Test
    void overviewDefaultKeyKeepsWorkingForBackwardCompatibility() {
        StateSnapshotMapper mapper = mock(StateSnapshotMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        StateSnapshotService service = new StateSnapshotService(mapper);

        service.store("finance", fresh("ledger-service:monthlyStats", Map.of("month", "2026-08")));

        verify(mapper).insert(org.mockito.ArgumentMatchers.<StateSnapshot>argThat(entity -> "overview".equals(entity.getSnapshotKey())));
    }
}
