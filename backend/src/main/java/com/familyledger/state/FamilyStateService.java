package com.familyledger.state;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FamilyStateService {
    private final StateSnapshotService snapshotService;
    private final Map<String, StateCollector> collectors;

    public FamilyStateService(StateSnapshotService snapshotService, List<StateCollector> collectors) {
        this.snapshotService = snapshotService;
        this.collectors = collectors.stream().collect(Collectors.toMap(StateCollector::domain, Function.identity()));
    }

    public StateDomainResponse get(String domain) { return snapshotService.get(domain); }

    public Map<String, StateDomainResponse> overview() {
        Map<String, StateDomainResponse> result = new LinkedHashMap<>();
        for (String domain : List.of("finance", "codex", "deepseek", "baby")) result.put(domain, get(domain));
        return result;
    }

    public StateDomainResponse refresh(String domain) {
        StateCollector collector = collectors.get(domain);
        if (collector == null) throw new IllegalArgumentException("不支持的状态域: " + domain);
        return snapshotService.store(domain, collector.collect());
    }
}
