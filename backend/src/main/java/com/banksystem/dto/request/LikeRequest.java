package com.banksystem.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.banksystem.common.validation.NoHtml;
import com.banksystem.common.validation.ValidationPatterns;
import com.banksystem.model.LikeInput;

/**
 * 新增 / 修改喜好商品的請求內容。
 * 總手續費、總金額不由前端傳入，一律由後端計算（避免被竄改）。
 */
public record LikeRequest(

        @NotBlank(message = "產品名稱為必填")
        @Size(max = 100, message = "產品名稱最多 100 字")
        @NoHtml
        String productName,

        @NotNull(message = "產品價格為必填")
        @DecimalMin(value = "0.01", message = "產品價格必須大於 0")
        @Digits(integer = 13, fraction = 2, message = "產品價格最多 13 位整數、2 位小數")
        BigDecimal price,

        @NotNull(message = "手續費率為必填")
        @DecimalMin(value = "0", message = "手續費率不可小於 0")
        @DecimalMax(value = "1", message = "手續費率不可大於 1（100%）")
        @Digits(integer = 1, fraction = 4, message = "手續費率最多 4 位小數")
        BigDecimal feeRate,

        @NotNull(message = "購買數量為必填")
        @Min(value = 1, message = "購買數量至少為 1")
        @Max(value = 1_000_000, message = "購買數量最多 1,000,000")
        Integer purchaseQuantity,

        @NotBlank(message = "扣款帳號為必填")
        @Pattern(regexp = ValidationPatterns.ACCOUNT, message = "扣款帳號須為 10–16 碼數字")
        String account) {

    public LikeInput toInput() {
        return new LikeInput(productName.strip(), price, feeRate, purchaseQuantity, account);
    }
}
