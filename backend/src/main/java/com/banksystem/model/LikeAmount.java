package com.banksystem.model;

import java.math.BigDecimal;

/** 系統計算出的金額：總手續費、預計扣款總金額（新台幣）。 */
public record LikeAmount(BigDecimal totalFee, BigDecimal totalAmount) {
}
