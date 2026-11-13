package com.banksystem.model;

import java.math.BigDecimal;

/** 使用者新增 / 修改喜好時輸入的資料（不含由系統計算的金額）。 */
public record LikeInput(
        String productName,
        BigDecimal price,
        BigDecimal feeRate,
        int purchaseQuantity,
        String account) {
}
