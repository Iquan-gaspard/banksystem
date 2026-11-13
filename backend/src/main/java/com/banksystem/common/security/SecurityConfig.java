package com.banksystem.common.security;

import java.util.Arrays;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.banksystem.dto.response.UserResponse;
import com.banksystem.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 登入與授權設定。
 *
 * - 登入：POST /api/auth/login（表單欄位 userId、password），成功建立 Session（HttpOnly cookie）
 * - 登出：POST /api/auth/logout
 * - 授權：/api/users/{userId}/** 只允許「登入者本人」存取，其他人 403、未登入 401
 * - CSRF：Cookie-to-header（見 {@link SpaCsrfTokenRequestHandler}）
 * - 密碼：BCrypt 雜湊比對
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** API 回應本身不需要載入任何資源，也不允許被嵌入 iframe。 */
    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    /** Swagger UI 頁面需載入自身的 JS / CSS（含 inline style），其餘限制同 API。 */
    private static final String SWAGGER_CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'";

    /** API 文件路徑（可由環境變數 SWAGGER_ENABLED=false 關閉，正式環境建議關閉）。 */
    private static final String[] SWAGGER_PATHS = {"/api/swagger-ui.html", "/api/swagger-ui/**", "/api/v3/api-docs/**"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
            UserService userService) throws Exception {
        JsonResponseWriter writer = new JsonResponseWriter(objectMapper);

        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher swaggerPaths = new OrRequestMatcher(
                Arrays.stream(SWAGGER_PATHS).<RequestMatcher>map(paths::matcher).toList());

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/"));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers("/api/users/{userId}", "/api/users/{userId}/**")
                                .access(SecurityConfig::isResourceOwner)
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("userId")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> writer.writeJson(
                                response, HttpStatus.OK, UserResponse.from(userService.getUser(authentication.getName()))))
                        // 不區分「帳號不存在」或「密碼錯誤」，避免被用來探測帳號
                        .failureHandler((request, response, exception) -> writer.writeProblem(
                                response, HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, e) -> writer.writeProblem(
                                response, HttpStatus.UNAUTHORIZED, "請先登入"))
                        .accessDeniedHandler((request, response, e) -> writer.writeProblem(
                                response, HttpStatus.FORBIDDEN, "沒有權限執行此操作")))
                .headers(headers -> headers
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(swaggerPaths,
                                new StaticHeadersWriter("Content-Security-Policy", SWAGGER_CSP)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(swaggerPaths),
                                new StaticHeadersWriter("Content-Security-Policy", API_CSP))));

        return http.build();
    }

    /** 網址中的 {userId} 必須是登入者本人。 */
    private static AuthorizationDecision isResourceOwner(Supplier<Authentication> authentication,
            RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        boolean isOwner = loggedIn && auth.getName().equals(context.getVariables().get("userId"));
        return new AuthorizationDecision(isOwner);
    }
}
