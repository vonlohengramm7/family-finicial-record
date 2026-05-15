package com.familyledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyledger.entity.Category;
import com.familyledger.mapper.CategoryMapper;
import com.familyledger.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> listAll() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSortOrder, Category::getId);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public Category create(Category category) {
        category.setId(null);
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public Category update(Category category) {
        categoryMapper.updateById(category);
        return category;
    }

    @Override
    public void delete(Long id) {
        categoryMapper.deleteById(id);
    }
}
