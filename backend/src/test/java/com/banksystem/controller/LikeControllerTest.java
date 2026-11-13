package com.banksystem.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
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

    private static LikeItem sampleItem() {
        return new LikeItem(10L, 1L, "全球債券基金A", new BigDecimal("1000"), new BigDecimal("0.01"), 3,
                "1111999666", new BigDecimal("30"), new BigDecimal("3030"), "test@email.com");
    }
}
