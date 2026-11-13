package com.banksystem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.banksystem.common.exception.BusinessRuleException;
import com.banksystem.model.LikeAmount;

/**
 * 業務規則：計算總手續費與預計扣款總金額（新台幣，四捨五入到整數元）。
 *
 * <pre>
 * 總手續費      = 價格 × 數量 × 手續費率
 * 預計扣款總金額 = 價格 × 數量 + 總手續費
 * </pre>
 *
 * 全程使用 BigDecimal，避免 double 的浮點誤差。
 */
@Component
public class FeeCalculator {

    /** 對應資料表欄位 DECIMAL(18,2) 的上限。 */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    public LikeAmount calculate(BigDecimal price, int quantity, BigDecimal feeRate) {
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));

        BigDecimal totalFee = subtotal.multiply(feeRate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.setScale(0, RoundingMode.HALF_UP).add(totalFee);

        if (totalAmount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("預計扣款總金額超過系統上限");
        }
        return new LikeAmount(totalFee, totalAmount);
    }
}
