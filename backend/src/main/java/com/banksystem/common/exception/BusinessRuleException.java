package com.banksystem.common.exception;

/** 違反業務規則（對應 HTTP 400），例如金額超過系統上限。 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
