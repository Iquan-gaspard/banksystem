package com.banksystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.banksystem.common.exception.BusinessRuleException;
import com.banksystem.model.LikeAmount;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @ParameterizedTest(name = "{0} × {1} × {2} → fee {3}, total {4}")
    @CsvSource({
            // price,  qty,  feeRate, totalFee, totalAmount
            "1000.00,  3,    0.01,    30,       3030",   // 一般情況
            "35.50,    1000, 0.0015,  53,       35553",  // 53.25 → 53
            "10.25,    200,  0,       0,        2050",   // 費率 0
            "12.34,    7,    0.015,   1,        87",     // 小計 86.38 → 86，手續費 1.2957 → 1
            "100,      1,    0.005,   1,        101",    // 0.5 → 四捨五入為 1（HALF_UP）
            "100,      1,    0.0049,  0,        100"     // 0.49 → 0
    })
    void calculatesFeeAndTotal(String price, int qty, String feeRate, String expectedFee, String expectedTotal) {
        LikeAmount amount = calculator.calculate(new BigDecimal(price), qty, new BigDecimal(feeRate));

        assertThat(amount.totalFee()).isEqualByComparingTo(expectedFee);
        assertThat(amount.totalAmount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void rejectsAmountOverSystemLimit() {
        BigDecimal maxPrice = new BigDecimal("9999999999999.99");

        assertThatThrownBy(() -> calculator.calculate(maxPrice, 1_000_000, BigDecimal.ONE))
                .isInstanceOf(BusinessRuleException.class);
    }
}
