package com.banksystem.dto.response;

import com.banksystem.model.User;

public record UserResponse(String userId, String userName, String email, String account) {

    public static UserResponse from(User user) {
        return new UserResponse(user.userId(), user.userName(), user.email(), user.account());
    }
}
