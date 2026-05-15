package com.familyledger.service;

import com.familyledger.entity.User;

import java.util.List;

public interface UserService {

    List<User> listAll();

    User getById(Long id);
}
