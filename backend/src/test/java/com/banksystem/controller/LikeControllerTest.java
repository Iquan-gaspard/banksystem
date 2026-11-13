package com.banksystem.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.banksystem.common.exception.ResourceNotFoundException;
import com.banksystem.model.LikeItem;
import com.banksystem.service.LikeService;

@WebMvcTest(LikeController.class)
class LikeControllerTest {

    private static final String BASE = "/api/users/A1236456789/likes";
    private static final String VALID_BODY = """
            {"productName":"全球債券基金A","price":1000,"feeRate":0.01,"purchaseQuantity":3,"account":"1111999666"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(likeService.create(eq("A1236456789"), any())).thenReturn(sampleItem());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/10")))
                .andExpect(jsonPath("$.totalFee").value(30))
                .andExpect(jsonPath("$.totalAmount").value(3030))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void createRejectsInvalidInputWithFieldErrors() throws Exception {
        String body = """
                {"productName":"<script>alert(1)</script>","price":-1,"feeRate":2,"purchaseQuantity":0,"account":"abc"}
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productName").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.feeRate").exists())
                .andExpect(jsonPath("$.errors.purchaseQuantity").exists())
                .andExpect(jsonPath("$.errors.account").exists());
        verify(likeService, never()).create(any(), any());
    }

    @Test
    void createRejectsClientSuppliedTotals() throws Exception {
        String body = """
                {"productName":"x","price":1,"feeRate":0,"purchaseQuantity":1,"account":"1111999666","totalFee":0}
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(likeService, never()).create(any(), any());
    }

    @ParameterizedTest(name = "邊界內：{0}")
    @CsvSource(delimiter = '|', value = {
            "價格最小 0.01      | 0.01 | 0      | 1       | 1111999666",
            "費率 = 1（100%）   | 1    | 1      | 1       | 1111999666",
            "數量上限 1,000,000 | 1    | 0      | 1000000 | 1111999666",
            "帳號 10 碼         | 1    | 0      | 1       | 1234567890",
            "帳號 16 碼         | 1    | 0      | 1       | 1234567890123456"
    })
    void acceptsBoundaryValues(String label, String price, String feeRate, String qty, String account)
            throws Exception {
        when(likeService.create(eq("A1236456789"), any())).thenReturn(sampleItem());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", price, feeRate, qty, account)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "超出邊界：{0}")
    @CsvSource(delimiter = '|', value = {
            "價格 0             | 0       | 0       | 1       | 1111999666",
            "價格 3 位小數      | 1.001   | 0       | 1       | 1111999666",
            "費率 5 位小數      | 1       | 0.00001 | 1       | 1111999666",
            "費率負數           | 1       | -0.1    | 1       | 1111999666",
            "費率 > 1           | 1       | 1.0001  | 1       | 1111999666",
            "數量 0             | 1       | 0       | 0       | 1111999666",
            "數量 1,000,001     | 1       | 0       | 1000001 | 1111999666",
            "數量為小數         | 1       | 0       | 1.5     | 1111999666",
            "帳號 9 碼          | 1       | 0       | 1       | 123456789",
            "帳號 17 碼         | 1       | 0       | 1       | 12345678901234567",
            "帳號含非數字       | 1       | 0       | 1       | 11119996a6"
    })
    void rejectsOutOfBoundaryValues(String label, String price, String feeRate, String qty, String account)
            throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", price, feeRate, qty, account)))
                .andExpect(status().isBadRequest());
        verify(likeService, never()).create(any(), any());
    }

    @Test
    void rejectsProductNameOver100Chars() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(101), "1", "0", "1", "1111999666")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productName").exists());
    }

    @Test
    void errorResponseDoesNotEchoUserInput() throws Exception {
        mockMvc.perform(get(BASE + "/{sn}", "abc<script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("請求格式錯誤"))
                // instance 欄位（RFC 7807 規定的請求路徑）為 URL 編碼，不會出現可執行的原始 HTML
                .andExpect(content().string(not(containsString("<script"))));
    }

    @Test
    void unknownPathDoesNotEchoPath() throws Exception {
        mockMvc.perform(get("/api/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("找不到資源"));
    }

    @Test
    void rejectsMalformedUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/likes", "A1' OR '1'='1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        when(likeService.getLike("A1236456789", 99L)).thenThrow(new ResourceNotFoundException("找不到喜好商品資料"));

        mockMvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/10"))
                .andExpect(status().isNoContent());
        verify(likeService).delete("A1236456789", 10L);
    }

    @Test
    void deleteReturns404WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("找不到喜好商品資料")).when(likeService).delete("A1236456789", 99L);

        mockMvc.perform(delete(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    private static String body(String name, String price, String feeRate, String qty, String account) {
        return """
                {"productName":"%s","price":%s,"feeRate":%s,"purchaseQuantity":%s,"account":"%s"}
                """.formatted(name, price, feeRate, qty, account);
    }

    private static LikeItem sampleItem() {
        return new LikeItem(10L, 1L, "全球債券基金A", new BigDecimal("1000"), new BigDecimal("0.01"), 3,
                "1111999666", new BigDecimal("30"), new BigDecimal("3030"), "test@email.com");
    }
}
