package com.carenest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CHỈ DÙNG KHI DEV/KIỂM THỬ (đăng ký khi {@code carenest.dev.clock.enabled=true}): đọc header {@code X-Dev-Now}
 * (ISO-8601 có offset, vd. {@code 2026-11-20T08:00:00+07:00}) và cho request chạy theo giờ đó.
 */
@Slf4j
public class DevClockFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Dev-Now";
    private static final String LOG_PREFIX = "[DevClockFilter]";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant simulated = parse(request.getHeader(HEADER));
        if (simulated == null) {
            chain.doFilter(request, response);
            return;
        }
        RequestAwareClock.simulate(simulated);
        try {
            chain.doFilter(request, response);
        } finally {
            // Thread của servlet được tái sử dụng: không xóa thì request sau của người khác bị lệch giờ
            RequestAwareClock.clearSimulation();
        }
    }

    static Instant parse(String header) {
        if (!StringUtils.hasText(header)) return null;
        try {
            return OffsetDateTime.parse(header.trim()).toInstant();
        } catch (DateTimeParseException ex) {
            log.warn("{}|parse|ERROR|type=invalidHeader", LOG_PREFIX);
            return null;
        }
    }
}
