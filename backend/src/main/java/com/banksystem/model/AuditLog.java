package com.banksystem.model;

import java.time.LocalDateTime;

/** 一筆操作紀錄；beforeData / afterData 為資料庫產生的 JSON 字串。 */
public record AuditLog(
        long id,
        String actorUserId,
        String action,
        long likeSn,
        String beforeData,
        String afterData,
        String clientIp,
        LocalDateTime createdAt) {
}
