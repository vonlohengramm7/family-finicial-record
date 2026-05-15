package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.entity.Project;
import com.familyledger.entity.Transaction;
import com.familyledger.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ApiResult<List<Project>>> list() {
        List<Project> list = projectService.listAll();
        return ResponseEntity.ok(ApiResult.success(list));
    }

    @PostMapping
    public ResponseEntity<ApiResult<Project>> create(@RequestBody Project project) {
        Project created = projectService.create(project);
        return ResponseEntity.status(201).body(ApiResult.success("创建成功", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<Project>> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        Project updated = projectService.update(project);
        return ResponseEntity.ok(ApiResult.success("更新成功", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok(ApiResult.success("删除成功", null));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResult<List<Transaction>>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Transaction> list = projectService.getTransactions(id, page, size);
        return ResponseEntity.ok(ApiResult.success(list));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResult<Map<String, Object>>> getStats(@PathVariable Long id) {
        Map<String, Object> stats = projectService.getStats(id);
        return ResponseEntity.ok(ApiResult.success(stats));
    }
}
