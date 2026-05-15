package com.familyledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.familyledger.entity.Transaction;
import com.familyledger.mapper.TransactionMapper;
import com.familyledger.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionMapper transactionMapper;

    @Override
    public Transaction getById(Long id) {
        return transactionMapper.selectById(id);
    }

    @Override
    public List<Transaction> list(Integer page, Integer size, Long userId, Long categoryId,
                                  List<Long> categoryIds, LocalDate startDate, LocalDate endDate,
                                  String keyword, BigDecimal minAmount, BigDecimal maxAmount) {
        LambdaQueryWrapper<Transaction> wrapper = buildQueryWrapper(userId, categoryId, categoryIds,
                startDate, endDate, keyword, minAmount, maxAmount);
        wrapper.orderByDesc(Transaction::getTransDate, Transaction::getCreatedAt);
        if (page != null && size != null) {
            Page<Transaction> p = transactionMapper.selectPage(new Page<>(page, size), wrapper);
            return p.getRecords();
        }
        return transactionMapper.selectList(wrapper);
    }

    @Override
    public long count(Long userId, Long categoryId, List<Long> categoryIds,
                      LocalDate startDate, LocalDate endDate,
                      String keyword, BigDecimal minAmount, BigDecimal maxAmount) {
        LambdaQueryWrapper<Transaction> wrapper = buildQueryWrapper(userId, categoryId, categoryIds,
                startDate, endDate, keyword, minAmount, maxAmount);
        return transactionMapper.selectCount(wrapper);
    }

    @Override
    public Transaction create(Transaction transaction) {
        transaction.setId(null);
        transactionMapper.insert(transaction);
        return transaction;
    }

    @Override
    public Transaction update(Transaction transaction) {
        transactionMapper.updateById(transaction);
        return transaction;
    }

    @Override
    public void delete(Long id) {
        transactionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(List<Transaction> transactions) {
        for (Transaction t : transactions) {
            t.setId(null);
            transactionMapper.insert(t);
        }
    }

    @Override
    public List<Map<String, Object>> monthlyStats(Long userId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Transaction::getTransDate, Transaction::getUserId, Transaction::getAmount);
        applyDateRange(wrapper, startDate, endDate);
        if (userId != null) {
            wrapper.eq(Transaction::getUserId, userId);
        }
        List<Transaction> list = transactionMapper.selectList(wrapper);

        Map<String, Map<String, BigDecimal>> monthlyMap = new LinkedHashMap<>();
        for (Transaction t : list) {
            if (t.getTransDate() == null || t.getAmount() == null) continue;
            String monthKey = t.getTransDate().toString().substring(0, 7);
            monthlyMap.putIfAbsent(monthKey, new LinkedHashMap<>());
            Map<String, BigDecimal> inner = monthlyMap.get(monthKey);
            inner.merge("total", t.getAmount(), BigDecimal::add);
            // Income vs expense
            if (t.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                inner.merge("income", t.getAmount(), BigDecimal::add);
            } else {
                inner.merge("expense", t.getAmount(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : monthlyMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", entry.getKey());
            item.putAll(entry.getValue());
            result.add(item);
        }
        result.sort(Comparator.comparing(m -> (String) m.get("month")));
        return result;
    }

    @Override
    public List<Map<String, Object>> categoryStats(Long userId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Transaction::getCategoryId, Transaction::getAmount)
               .isNotNull(Transaction::getCategoryId);
        applyDateRange(wrapper, startDate, endDate);
        if (userId != null) {
            wrapper.eq(Transaction::getUserId, userId);
        }
        List<Transaction> list = transactionMapper.selectList(wrapper);

        Map<Long, Map<String, BigDecimal>> categoryMap = new LinkedHashMap<>();
        for (Transaction t : list) {
            if (t.getCategoryId() == null || t.getAmount() == null) continue;
            Long cid = t.getCategoryId();
            categoryMap.putIfAbsent(cid, new LinkedHashMap<>());
            Map<String, BigDecimal> inner = categoryMap.get(cid);
            inner.merge("total", t.getAmount(), BigDecimal::add);
            inner.merge("count", BigDecimal.ONE, BigDecimal::add);
            if (t.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                inner.merge("income", t.getAmount(), BigDecimal::add);
            } else {
                inner.merge("expense", t.getAmount(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : categoryMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("categoryId", entry.getKey());
            item.putAll(entry.getValue());
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> userStats(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Transaction::getUserId, Transaction::getAmount)
               .isNotNull(Transaction::getUserId);
        applyDateRange(wrapper, startDate, endDate);
        List<Transaction> list = transactionMapper.selectList(wrapper);

        Map<Long, Map<String, BigDecimal>> userMap = new LinkedHashMap<>();
        for (Transaction t : list) {
            if (t.getUserId() == null || t.getAmount() == null) continue;
            Long uid = t.getUserId();
            userMap.putIfAbsent(uid, new LinkedHashMap<>());
            Map<String, BigDecimal> inner = userMap.get(uid);
            inner.merge("total", t.getAmount(), BigDecimal::add);
            inner.merge("count", BigDecimal.ONE, BigDecimal::add);
            if (t.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                inner.merge("income", t.getAmount(), BigDecimal::add);
            } else {
                inner.merge("expense", t.getAmount(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : userMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", entry.getKey());
            item.putAll(entry.getValue());
            result.add(item);
        }
        return result;
    }

    private LambdaQueryWrapper<Transaction> buildQueryWrapper(Long userId, Long categoryId,
                                                               List<Long> categoryIds,
                                                               LocalDate startDate, LocalDate endDate,
                                                               String keyword, BigDecimal minAmount, BigDecimal maxAmount) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Transaction::getUserId, userId);
        }
        // Single categoryId (exact match, backward compat)
        if (categoryId != null) {
            wrapper.eq(Transaction::getCategoryId, categoryId);
        }
        // Multiple categoryIds (e.g. when user selects a parent category)
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(Transaction::getCategoryId, categoryIds);
        }
        applyDateRange(wrapper, startDate, endDate);
        if (StringUtils.isNotBlank(keyword)) {
            try {
                BigDecimal kwAmount = new BigDecimal(keyword);
                BigDecimal negAmount = kwAmount.negate();
                wrapper.and(w -> w.like(Transaction::getNote, keyword)
                                    .or().eq(Transaction::getAmount, kwAmount)
                                    .or().eq(Transaction::getAmount, negAmount));
            } catch (NumberFormatException e) {
                wrapper.like(Transaction::getNote, keyword);
            }
        }
        if (minAmount != null) {
            wrapper.ge(Transaction::getAmount, minAmount);
        }
        if (maxAmount != null) {
            wrapper.le(Transaction::getAmount, maxAmount);
        }
        return wrapper;
    }

    private void applyDateRange(LambdaQueryWrapper<Transaction> wrapper, LocalDate startDate, LocalDate endDate) {
        if (startDate != null) {
            wrapper.ge(Transaction::getTransDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(Transaction::getTransDate, endDate);
        }
    }
}
