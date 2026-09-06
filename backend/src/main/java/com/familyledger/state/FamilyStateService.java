package com.familyledger.state;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class FamilyStateService {
    private static final Set<String> SUPPORTED_DOMAINS = Set.of("finance", "codex", "opencodeGo", "deepseek", "baby");
    private final StateSnapshotService snapshotService;

    public FamilyStateService(StateSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public StateDomainResponse get(String domain) {
        if (!SUPPORTED_DOMAINS.contains(domain)) {
            return StateDomainResponse.builder()
                    .domain(domain)
                    .status(StateStatus.UNAVAILABLE)
                    .source("state-snapshot:" + domain)
                    .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(0).build())
                    .data(Map.of())
                    .errorCode("UNKNOWN_DOMAIN")
                    .errorMessage("不支持的状态域")
                    .build();
        }
        return withDomain(domain, snapshotService.get(domain));
    }

    public Map<String, StateDomainResponse> overview() {
        Map<String, StateDomainResponse> result = new LinkedHashMap<>();
        for (String domain : SUPPORTED_DOMAINS) result.put(domain, get(domain));
        return result;
    }

    private StateDomainResponse withDomain(String domain, StateDomainResponse response) {
        return StateDomainResponse.builder()
                .domain(domain)
                .status(response.getStatus())
                .source(response.getSource())
                .observedAt(response.getObservedAt())
                .freshness(response.getFreshness())
                .data(response.getData())
                .errorCode(response.getErrorCode())
                .errorMessage(response.getErrorMessage())
                .nextRefreshAt(response.getNextRefreshAt())
                .build();
    }
}
