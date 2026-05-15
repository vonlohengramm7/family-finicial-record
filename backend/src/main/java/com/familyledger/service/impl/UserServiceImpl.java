package com.familyledger.service.impl;

import com.familyledger.entity.User;
import com.familyledger.mapper.UserMapper;
import com.familyledger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public List<User> listAll() {
        return userMapper.selectList(null);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
