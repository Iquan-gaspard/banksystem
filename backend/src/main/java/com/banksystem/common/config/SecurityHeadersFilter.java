package com.banksystem.common.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 回應加上安全 header：
 * - X-Content-Type-Options: nosniff → 瀏覽器不會把 JSON 當成 HTML 執行（防 XSS）
 * - Content-Security-Policy → API 回應本身不允許載入任何資源、不可被嵌入 iframe
 * - Cache-Control: no-store → 金融資料不留在瀏覽器快取
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
