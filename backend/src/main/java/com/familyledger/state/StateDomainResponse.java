package com.familyledger.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class StateDomainResponse {
    String domain;
    StateStatus status;
    String source;
    LocalDateTime observedAt;
    Freshness freshness;
    Map<String, Object> data;
    String errorCode;
    String errorMessage;
    LocalDateTime nextRefreshAt;

    @JsonIgnore
    public Freshness getFreshness() {
        return freshness;
    }

    @JsonProperty("freshUntil")
    public LocalDateTime getFreshUntil() {
        return freshness == null ? null : freshness.getExpiresAt();
    }

    @JsonIgnore
    public String getErrorCode() {
        return errorCode;
    }

    @JsonIgnore
    public String getErrorMessage() {
        return errorMessage;
    }

    @JsonProperty("error")
    public Map<String, String> getError() {
        if (errorCode == null) {
            return null;
        }
        Map<String, String> error = new LinkedHashMap<>();
        error.put("code", errorCode);
        error.put("reason", errorMessage);
        return error;
    }

    @Value
    @Builder
    public static class Freshness {
        long ttlSeconds;
        LocalDateTime expiresAt;
    }
}
