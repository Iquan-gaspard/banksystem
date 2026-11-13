package com.banksystem.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全域例外處理：所有錯誤統一回傳 RFC 7807 ProblemDetail（application/problem+json）。
 * 不回傳 stack trace、SQL 訊息或使用者原始輸入，避免資訊外洩與反射型 XSS。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生錯誤，請稍後再試");
    }

    /** @Valid @RequestBody 驗證失敗。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return validationFailed(errors);
    }

    /** 方法參數驗證失敗（例如 @PathVariable 上的 @Pattern，或與其並存的 @RequestBody）。 */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> errors.putIfAbsent(name, error.getDefaultMessage()));
            }
        }
        return validationFailed(errors);
    }

    /**
     * Spring MVC 內建例外（JSON 格式錯誤、型別轉換失敗、路徑不存在、方法不支援…）的預設訊息
     * 可能包含使用者輸入（例如 "Failed to convert 'sn' with value: 'abc'"），
     * 一律改成固定文字，不把使用者輸入原樣回傳。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // 部分例外的 body 由 super 才建立，因此在 super 之後再覆寫 detail
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setDetail(genericDetail(statusCode));
        }
        return response;
    }

    private static String genericDetail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "請求格式錯誤";
            case 404 -> "找不到資源";
            case 405 -> "不支援的 HTTP 方法";
            case 406, 415 -> "不支援的內容類型";
            default -> status.is5xxServerError() ? "系統發生錯誤，請稍後再試" : "請求無法處理";
        };
    }

    private ResponseEntity<Object> validationFailed(Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "輸入資料格式錯誤");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
