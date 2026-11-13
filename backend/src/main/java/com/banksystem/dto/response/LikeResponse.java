package com.banksystem.dto.response;

import java.math.BigDecimal;

import com.banksystem.model.LikeItem;

/** 喜好商品回應：產品資訊、扣款帳號、總手續費、預計扣款總金額、使用者 Email。 */
public record LikeResponse(
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

    public static LikeResponse from(LikeItem item) {
        return new LikeResponse(
                item.sn(),
                item.productNo(),
                item.productName(),
                item.price(),
                item.feeRate(),
                item.purchaseQuantity(),
                item.account(),
                item.totalFee(),
                item.totalAmount(),
                item.email());
    }
}
