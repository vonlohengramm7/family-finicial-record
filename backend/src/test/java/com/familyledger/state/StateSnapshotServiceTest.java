package com.familyledger.state;

import com.familyledger.entity.StateSnapshot;
import com.familyledger.mapper.StateSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateSnapshotServiceTest {

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
}
