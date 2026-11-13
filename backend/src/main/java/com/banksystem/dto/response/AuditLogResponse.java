package com.banksystem.dto.response;

import java.time.LocalDateTime;

import com.banksystem.model.AuditLog;
import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * 操作紀錄回應。before / after 為資料庫以 JSON_OBJECT 產生的 JSON，直接原樣輸出為物件。
 */
public record AuditLogResponse(
        long id,
        String actorUserId,
        String action,
        long likeSn,
        @JsonRawValue String before,
        @JsonRawValue String after,
        String clientIp,
        LocalDateTime createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.id(), log.actorUserId(), log.action(), log.likeSn(),
                log.beforeData(), log.afterData(), log.clientIp(), log.createdAt());
    }
}
