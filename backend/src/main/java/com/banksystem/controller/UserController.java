package com.banksystem.controller;

import java.util.List;

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

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = "使用者 ID 格式錯誤") String userId) {
        return UserResponse.from(userService.getUser(userId));
    }
}
