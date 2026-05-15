package com.familyledger.service;

import com.familyledger.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TransactionService {

    Transaction getById(Long id);

    List<Transaction> list(Integer page, Integer size, Long userId, Long categoryId, List<Long> categoryIds,
                           LocalDate startDate, LocalDate endDate, String keyword, BigDecimal minAmount, BigDecimal maxAmount);

    long count(Long userId, Long categoryId, List<Long> categoryIds, LocalDate startDate, LocalDate endDate,
               String keyword, BigDecimal minAmount, BigDecimal maxAmount);

    Transaction create(Transaction transaction);

    Transaction update(Transaction transaction);

    void delete(Long id);

    void batchCreate(List<Transaction> transactions);

    List<Map<String, Object>> monthlyStats(Long userId, LocalDate startDate, LocalDate endDate);

    List<Map<String, Object>> categoryStats(Long userId, LocalDate startDate, LocalDate endDate);

    List<Map<String, Object>> userStats(LocalDate startDate, LocalDate endDate);
}
