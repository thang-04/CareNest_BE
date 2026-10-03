package com.carenest.security;

import com.carenest.dto.response.ApiResponse;
import com.carenest.exception.AppExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ghi lỗi 401/403 của security filter chain theo cùng format với AppExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final AppExceptionHandler exceptionHandler;
    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, RuntimeException ex) throws IOException {
        ResponseEntity<ApiResponse<Void>> error = exceptionHandler.handleSecurityException(ex);
        response.setStatus(error.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), error.getBody());
    }
}
