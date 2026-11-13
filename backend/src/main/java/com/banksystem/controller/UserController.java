package com.banksystem.controller;

import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banksystem.common.validation.ValidationPatterns;
import com.banksystem.dto.response.UserResponse;
import com.banksystem.service.UserService;

/** 展示層：使用者 API。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 單一使用者（只能查詢自己，由 SecurityConfig 授權規則控管）。 */
    @GetMapping("/{userId}")
    public UserResponse getUser(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = "使用者 ID 格式錯誤") String userId) {
        return UserResponse.from(userService.getUser(userId));
    }
}
