package com.familyledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.familyledger.entity.Project;
import com.familyledger.entity.Transaction;
import com.familyledger.mapper.ProjectMapper;
import com.familyledger.mapper.TransactionMapper;
import com.familyledger.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final TransactionMapper transactionMapper;

    @Override
    public List<Project> listAll() {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Project::getCreatedAt);
        return projectMapper.selectList(wrapper);
    }

    @Override
    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public Project create(Project project) {
        project.setId(null);
        projectMapper.insert(project);
        return project;
    }

    @Override
    public Project update(Project project) {
        projectMapper.updateById(project);
        return project;
    }

    @Override
    public void delete(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public List<Transaction> getTransactions(Long projectId, Integer page, Integer size) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getProjectId, projectId)
               .orderByDesc(Transaction::getTransDate, Transaction::getCreatedAt);
        if (page != null && size != null) {
            Page<Transaction> p = transactionMapper.selectPage(new Page<>(page, size), wrapper);
            return p.getRecords();
        }
        return transactionMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getStats(Long projectId) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getProjectId, projectId);
        List<Transaction> list = transactionMapper.selectList(wrapper);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        int count = 0;
        for (Transaction t : list) {
            if (t.getAmount() == null) continue;
            count++;
            if (t.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", count);
        stats.put("totalIncome", totalIncome);
        stats.put("totalExpense", totalExpense);
        stats.put("netAmount", totalIncome.add(totalExpense));
        return stats;
    }
}
