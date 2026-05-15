package com.familyledger.controller;

import com.familyledger.config.ApiResult;
import com.familyledger.entity.User;
import com.familyledger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResult<List<User>>> list() {
        List<User> list = userService.listAll();
        return ResponseEntity.ok(ApiResult.success(list));
    }
}
