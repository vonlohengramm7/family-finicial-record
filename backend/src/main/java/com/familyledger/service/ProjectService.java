package com.familyledger.service;

import com.familyledger.entity.Project;
import com.familyledger.entity.Transaction;

import java.util.List;
import java.util.Map;

public interface ProjectService {

    List<Project> listAll();

    Project getById(Long id);

    Project create(Project project);

    Project update(Project project);

    void delete(Long id);

    List<Transaction> getTransactions(Long projectId, Integer page, Integer size);

    Map<String, Object> getStats(Long projectId);
}
