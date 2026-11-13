package com.banksystem.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banksystem.dto.response.UserResponse;
import com.banksystem.service.UserService;

/**
 * 展示層：登入狀態。
 * 登入（POST /api/auth/login）與登出（POST /api/auth/logout）由 Spring Security 處理，見 SecurityConfig。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 目前登入的使用者；未登入時由 Spring Security 回 401。 */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
        return UserResponse.from(userService.getUser(principal.getUsername()));
    }
}
