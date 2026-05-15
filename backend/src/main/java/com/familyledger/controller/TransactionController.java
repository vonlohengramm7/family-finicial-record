package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.config.PageResult;
import com.familyledger.entity.Transaction;
import com.familyledger.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<ApiResult<PageResult<Transaction>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {

        long total = transactionService.count(userId, categoryId, startDate, endDate, keyword);
        List<Transaction> records = transactionService.list(page, size, userId, categoryId, startDate, endDate, keyword);
        PageResult<Transaction> pageResult = new PageResult<>(records, total, page, size);
        return ResponseEntity.ok(ApiResult.success(pageResult));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<Transaction>> getById(@PathVariable Long id) {
        Transaction t = transactionService.getById(id);
        if (t == null) {
            return ResponseEntity.status(404).body(ApiResult.error(404, "交易记录不存在"));
        }
        return ResponseEntity.ok(ApiResult.success(t));
    }

    @PostMapping
    public ResponseEntity<ApiResult<Transaction>> create(@RequestBody Transaction transaction) {
        Transaction created = transactionService.create(transaction);
        return ResponseEntity.status(201).body(ApiResult.success("创建成功", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<Transaction>> update(@PathVariable Long id, @RequestBody Transaction transaction) {
        transaction.setId(id);
        Transaction updated = transactionService.update(transaction);
        return ResponseEntity.ok(ApiResult.success("更新成功", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.ok(ApiResult.success("删除成功", null));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResult<Void>> batchCreate(@RequestBody List<Transaction> transactions) {
        transactionService.batchCreate(transactions);
        return ResponseEntity.status(201).body(ApiResult.success("批量创建成功", null));
    }
}
