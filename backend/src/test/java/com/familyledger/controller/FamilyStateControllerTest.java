package com.familyledger.controller;

import com.familyledger.config.GlobalExceptionHandler;
import com.familyledger.state.FamilyStateService;
import com.familyledger.state.StateDomainResponse;
import com.familyledger.state.StateStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FamilyStateControllerTest {

    @Mock
    private FamilyStateService familyStateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FamilyStateController(familyStateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesFrozenOverviewRouteWithApiResultEnvelope() throws Exception {
        when(familyStateService.overview()).thenReturn(Map.of("finance", freshResponse()));

        mockMvc.perform(get("/api/family-status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.finance.status").value("FRESH"))
                .andExpect(jsonPath("$.data.finance.source").value("ledger-service:monthlyStats"))
                .andExpect(jsonPath("$.data.finance.observedAt").exists())
                .andExpect(jsonPath("$.data.finance.freshUntil").exists());
    }

    @Test
    void exposesUnknownDomainAsExplicitUnavailableStatus() throws Exception {
        when(familyStateService.get("unsupported")).thenReturn(StateDomainResponse.builder()
                .status(StateStatus.UNAVAILABLE)
                .source("state-snapshot:unsupported")
                .data(Map.of())
                .errorCode("UNKNOWN_DOMAIN")
                .errorMessage("不支持的状态域")
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(0).build())
                .build());

        mockMvc.perform(get("/api/family-status/unsupported").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.error.code").value("UNKNOWN_DOMAIN"));
    }

    @Test
    void hasNoRefreshWriteEndpoint() throws Exception {
        mockMvc.perform(post("/api/family-status/refresh/finance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void removedLegacyStateRouteReturnsNotFoundInsteadOfInternalError() throws Exception {
        mockMvc.perform(get("/api/state/overview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void convertsMissingStaticResourceToNotFoundEnvelope() {
        ResponseEntity<?> response = new GlobalExceptionHandler().handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/state/overview"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    private StateDomainResponse freshResponse() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 8, 16, 8, 0);
        LocalDateTime freshUntil = observedAt.plusMinutes(15);
        return StateDomainResponse.builder()
                .status(StateStatus.FRESH)
                .source("ledger-service:monthlyStats")
                .observedAt(observedAt)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(900).expiresAt(freshUntil).build())
                .data(Map.of("month", "2026-08"))
                .nextRefreshAt(freshUntil)
                .build();
    }
}
