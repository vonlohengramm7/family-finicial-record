package com.familyledger.state;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class StateDomainResponse {
    StateStatus status;
    String source;
    LocalDateTime observedAt;
    Freshness freshness;
    Map<String, Object> data;
    String errorCode;
    String errorMessage;
    LocalDateTime nextRefreshAt;

    @Value
    @Builder
    public static class Freshness {
        long ttlSeconds;
        LocalDateTime expiresAt;
    }
}
