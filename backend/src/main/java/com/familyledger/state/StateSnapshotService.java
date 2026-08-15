package com.familyledger.state;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyledger.entity.StateSnapshot;
import com.familyledger.mapper.StateSnapshotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StateSnapshotService {
    private final StateSnapshotMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 首期统一键；DeepSeek 按 Key 维度使用 by_api_key:<stable-id>，绝不包含 Key 原文。 */
    public static final String DEFAULT_SNAPSHOT_KEY = "overview";

    public StateSnapshotService(StateSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    public StateDomainResponse store(String domain, StateDomainResponse result) {
        return store(domain, DEFAULT_SNAPSHOT_KEY, result);
    }

    public StateDomainResponse store(String domain, String key, StateDomainResponse result) {
        StateSnapshot previous = mapper.selectOne(new LambdaQueryWrapper<StateSnapshot>()
                .eq(StateSnapshot::getDomain, domain).eq(StateSnapshot::getSnapshotKey, key));
        if (result.getStatus() == StateStatus.UNAVAILABLE && previous != null && previous.getPayloadJson() != null) {
            previous.setStatus(StateStatus.STALE.name());
            previous.setErrorCode(result.getErrorCode());
            previous.setErrorMessage(result.getErrorMessage());
            previous.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(previous);
            return toResponse(previous, StateStatus.STALE);
        }
        StateSnapshot snapshot = previous == null ? new StateSnapshot() : previous;
        snapshot.setDomain(domain);
        snapshot.setSnapshotKey(key);
        snapshot.setPayloadJson(write(result.getData()));
        snapshot.setSource(result.getSource());
        snapshot.setObservedAt(result.getObservedAt());
        snapshot.setFreshUntil(result.getFreshness().getExpiresAt());
        snapshot.setStatus(result.getStatus().name());
        snapshot.setErrorCode(result.getErrorCode());
        snapshot.setErrorMessage(result.getErrorMessage());
        snapshot.setUpdatedAt(LocalDateTime.now());
        if (previous == null) {
            snapshot.setCreatedAt(LocalDateTime.now());
            mapper.insert(snapshot);
        } else {
            mapper.updateById(snapshot);
        }
        return result;
    }

    public StateDomainResponse get(String domain) {
        return get(domain, DEFAULT_SNAPSHOT_KEY);
    }

    public StateDomainResponse get(String domain, String key) {
        StateSnapshot snapshot = mapper.selectOne(new LambdaQueryWrapper<StateSnapshot>()
                .eq(StateSnapshot::getDomain, domain).eq(StateSnapshot::getSnapshotKey, key));
        if (snapshot == null) {
            return unavailable(domain, "SNAPSHOT_MISSING", "尚无成功快照，请手动刷新");
        }
        StateStatus status = StateStatus.valueOf(snapshot.getStatus());
        if (status == StateStatus.FRESH && snapshot.getFreshUntil() != null && snapshot.getFreshUntil().isBefore(LocalDateTime.now())) {
            status = StateStatus.STALE;
        }
        return toResponse(snapshot, status);
    }

    private StateDomainResponse toResponse(StateSnapshot snapshot, StateStatus status) {
        return StateDomainResponse.builder().status(status).source(snapshot.getSource()).observedAt(snapshot.getObservedAt())
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(ttl(snapshot)).expiresAt(snapshot.getFreshUntil()).build())
                .data(read(snapshot.getPayloadJson())).errorCode(snapshot.getErrorCode()).errorMessage(snapshot.getErrorMessage())
                .nextRefreshAt(snapshot.getFreshUntil()).build();
    }

    private long ttl(StateSnapshot snapshot) {
        if (snapshot.getObservedAt() == null || snapshot.getFreshUntil() == null) return 0;
        return Math.max(0, java.time.Duration.between(snapshot.getObservedAt(), snapshot.getFreshUntil()).getSeconds());
    }

    private StateDomainResponse unavailable(String domain, String errorCode, String errorMessage) {
        return StateDomainResponse.builder().status(StateStatus.UNAVAILABLE).source("state-snapshot:" + domain)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(0).build()).data(Map.of())
                .errorCode(errorCode).errorMessage(errorMessage).build();
    }

    private String write(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("状态快照序列化失败", exception); }
    }

    private Map<String, Object> read(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { return Map.of(); }
    }
}
