package com.banksystem.controller;

import java.util.List;

import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banksystem.common.validation.ValidationPatterns;
import com.banksystem.dto.response.AuditLogResponse;
import com.banksystem.service.AuditLogService;

/** 展示層：操作紀錄查詢（只能查自己的，由 SecurityConfig 授權規則控管）。 */
@RestController
@RequestMapping("/api/users/{userId}/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> getAuditLogs(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = "使用者 ID 格式錯誤") String userId) {
        return auditLogService.getAuditLogs(userId).stream().map(AuditLogResponse::from).toList();
    }
}
