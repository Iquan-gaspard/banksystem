package com.banksystem.model;

import java.math.BigDecimal;

/** 喜好清單的一筆資料（like_list ⋈ product ⋈ users 的查詢結果）。 */
public record LikeItem(
        long sn,
        long productNo,
        String productName,
        BigDecimal price,
        BigDecimal feeRate,
        int purchaseQuantity,
        String account,
        BigDecimal totalFee,
        BigDecimal totalAmount,
        String email) {
}
