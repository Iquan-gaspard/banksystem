package com.banksystem.model;

/** 使用者（對應 users 資料表）。 */
public record User(String userId, String userName, String email, String account) {
}
