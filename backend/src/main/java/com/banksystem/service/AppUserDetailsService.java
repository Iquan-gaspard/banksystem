package com.banksystem.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.banksystem.common.validation.ValidationPatterns;
import com.banksystem.repository.UserRepository;

/** 業務層：登入時依 userId 載入帳號與密碼雜湊（透過 Stored Procedure）。 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) {
        if (userId == null || !userId.matches(ValidationPatterns.USER_ID)) {
            throw new UsernameNotFoundException("User not found");
        }
        return userRepository.findCredentials(userId)
                // 使用資料庫中的 user_id 作為登入身分，確保與網址比對時大小寫一致
                .map(credentials -> User.withUsername(credentials.userId())
                        .password(credentials.passwordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
