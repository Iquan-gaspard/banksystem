package com.banksystem.common.validation;

/** 共用的輸入格式規則（白名單）。 */
public final class ValidationPatterns {

    /** 使用者 ID：1–20 碼英數字。 */
    public static final String USER_ID = "^[A-Za-z0-9]{1,20}$";

    /** 扣款帳號：10–16 碼數字。 */
    public static final String ACCOUNT = "^\\d{10,16}$";

    private ValidationPatterns() {
    }
}
