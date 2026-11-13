package com.banksystem.common.exception;

/** 查無資料（對應 HTTP 404）。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
