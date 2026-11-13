package com.banksystem.model;

/** 執行操作的人（登入者）與來源 IP，寫入操作紀錄用。 */
public record Actor(String userId, String clientIp) {
}
