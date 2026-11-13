package com.banksystem.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Security filter 層（還沒進到 Controller）直接輸出 JSON / ProblemDetail 回應。 */
public class JsonResponseWriter {

    private final ObjectMapper objectMapper;

    public JsonResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeJson(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        write(response, status, MediaType.APPLICATION_JSON_VALUE, body);
    }

    public void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        write(response, status, MediaType.APPLICATION_PROBLEM_JSON_VALUE, ProblemDetail.forStatusAndDetail(status, detail));
    }

    private void write(HttpServletResponse response, HttpStatus status, String contentType, Object body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
