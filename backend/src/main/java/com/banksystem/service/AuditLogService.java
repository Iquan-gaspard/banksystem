package com.banksystem.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.banksystem.model.AuditLog;
import com.banksystem.repository.AuditLogRepository;

/** 業務層：操作紀錄查詢。 */
@Service
public class AuditLogService {

    /** 畫面上最多顯示的筆數。 */
    static final int MAX_RECORDS = 50;

    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    public AuditLogService(AuditLogRepository auditLogRepository, UserService userService) {
        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
    }

    public List<AuditLog> getAuditLogs(String userId) {
        userService.getUser(userId);
        return auditLogRepository.findByOwner(userId, MAX_RECORDS);
    }
}
