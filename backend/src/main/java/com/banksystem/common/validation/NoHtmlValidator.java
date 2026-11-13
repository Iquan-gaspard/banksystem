package com.banksystem.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 交給 @NotBlank / @NotNull 判斷
        return value == null || (value.indexOf('<') < 0 && value.indexOf('>') < 0);
    }
}
