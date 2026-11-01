package com.carenest.security;

import com.carenest.utils.ApiCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 403 phát sinh trong filter chain; 403 từ @PreAuthorize do GlobalExceptionHandler xử lý. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn(
                "[handle]|path={}|type={}",
                request.getRequestURI(),
                accessDeniedException.getClass().getSimpleName());
        SecurityResponseWriter.write(response, objectMapper, ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getDefaultDesc());
    }
}
