package com.familyledger.service;

import com.familyledger.entity.Category;

import java.util.List;

public interface CategoryService {

    List<Category> listAll();

    Category getById(Long id);

    Category create(Category category);

    Category update(Category category);

    void delete(Long id);
}
