package com.banksystem.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.banksystem.common.exception.ResourceNotFoundException;
import com.banksystem.model.User;
import com.banksystem.repository.UserRepository;

/** 業務層：使用者。 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }

    /** 取得使用者，不存在則丟出 404。 */
    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到使用者"));
    }
}
