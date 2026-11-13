package com.banksystem.model;

/** 登入驗證用的帳號與密碼雜湊（只在後端使用，不會回傳給前端）。 */
public record UserCredentials(String userId, String passwordHash) {
}
