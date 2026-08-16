package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.state.FamilyStateService;
import com.familyledger.state.StateDomainResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/family-status")
@RequiredArgsConstructor
public class FamilyStateController {
    private final FamilyStateService familyStateService;

    @GetMapping
    public ResponseEntity<ApiResult<Map<String, StateDomainResponse>>> overview() {
        return ResponseEntity.ok(ApiResult.success(familyStateService.overview()));
    }

    @GetMapping("/{domain}")
    public ResponseEntity<ApiResult<StateDomainResponse>> domain(@PathVariable String domain) {
        return ResponseEntity.ok(ApiResult.success(familyStateService.get(domain)));
    }

}
