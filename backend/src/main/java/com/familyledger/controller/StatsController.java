package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final TransactionService transactionService;

    @GetMapping("/monthly")
    public ResponseEntity<ApiResult<List<Map<String, Object>>>> monthly(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> stats = transactionService.monthlyStats(userId, startDate, endDate);
        return ResponseEntity.ok(ApiResult.success(stats));
    }

    @GetMapping("/by-category")
    public ResponseEntity<ApiResult<List<Map<String, Object>>>> byCategory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> stats = transactionService.categoryStats(userId, startDate, endDate);
        return ResponseEntity.ok(ApiResult.success(stats));
    }

    @GetMapping("/by-user")
    public ResponseEntity<ApiResult<List<Map<String, Object>>>> byUser(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> stats = transactionService.userStats(startDate, endDate);
        return ResponseEntity.ok(ApiResult.success(stats));
    }
}
