package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.entity.Category;
import com.familyledger.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResult<List<Category>>> list() {
        List<Category> list = categoryService.listAll();
        return ResponseEntity.ok(ApiResult.success(list));
    }

    @PostMapping
    public ResponseEntity<ApiResult<Category>> create(@RequestBody Category category) {
        Category created = categoryService.create(category);
        return ResponseEntity.status(201).body(ApiResult.success("创建成功", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<Category>> update(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        Category updated = categoryService.update(category);
        return ResponseEntity.ok(ApiResult.success("更新成功", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResult.success("删除成功", null));
    }
}
