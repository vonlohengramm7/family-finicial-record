package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.state.FamilyStateService;
import com.familyledger.state.StateDomainResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/state")
@RequiredArgsConstructor
public class FamilyStateController {
    private final FamilyStateService familyStateService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResult<Map<String, StateDomainResponse>>> overview() {
        return ResponseEntity.ok(ApiResult.success(familyStateService.overview()));
    }

    @GetMapping("/{domain:finance|codex|deepseek|baby}")
    public ResponseEntity<ApiResult<StateDomainResponse>> domain(@PathVariable String domain) {
        return ResponseEntity.ok(ApiResult.success(familyStateService.get(domain)));
    }

    @PostMapping("/refresh/{domain:finance|codex|deepseek|baby}")
    public ResponseEntity<ApiResult<StateDomainResponse>> refresh(@PathVariable String domain) {
        return ResponseEntity.ok(ApiResult.success(familyStateService.refresh(domain)));
    }
}
