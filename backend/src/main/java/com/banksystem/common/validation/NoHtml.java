package com.banksystem.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 防 XSS：文字欄位不得包含 HTML 標籤字元（&lt; 與 &gt;）。
 * 與前端 Vue 自動跳脫、CSP header 搭配，形成多層防禦。
 */
@Documented
@Constraint(validatedBy = NoHtmlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoHtml {

    String message() default "不可包含 < 或 > 字元";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
