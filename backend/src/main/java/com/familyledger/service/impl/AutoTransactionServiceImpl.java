package com.familyledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyledger.entity.AutoTransaction;
import com.familyledger.mapper.AutoTransactionMapper;
import com.familyledger.service.AutoTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AutoTransactionServiceImpl implements AutoTransactionService {

    private final AutoTransactionMapper autoTransactionMapper;

    @Override
    public List<AutoTransaction> listActive() {
        return autoTransactionMapper.selectList(new LambdaQueryWrapper<AutoTransaction>()
                .eq(AutoTransaction::getIsActive, true)
                .orderByAsc(AutoTransaction::getNextRunDate));
    }
}
