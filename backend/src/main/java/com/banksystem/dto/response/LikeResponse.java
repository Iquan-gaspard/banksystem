package com.banksystem.dto.response;

import java.math.BigDecimal;

import com.banksystem.model.LikeItem;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 喜好商品回應：產品資訊、扣款帳號、總手續費、預計扣款總金額、使用者 Email。
 * 金額與費率以十進位字串輸出（例如 "1000.00"），避免前端 JavaScript Number 在大數值時失去精度。
 */
public record LikeResponse(
        long sn,
        long productNo,
        String productName,
        @Schema(example = "1000.00") String price,
        @Schema(example = "0.0100") String feeRate,
        int purchaseQuantity,
        String account,
        @Schema(example = "30.00") String totalFee,
        @Schema(example = "3030.00") String totalAmount,
        String email) {

    public static LikeResponse from(LikeItem item) {
        return new LikeResponse(
                item.sn(),
                item.productNo(),
                item.productName(),
                plain(item.price()),
                plain(item.feeRate()),
                item.purchaseQuantity(),
                item.account(),
                plain(item.totalFee()),
                plain(item.totalAmount()),
                item.email());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
