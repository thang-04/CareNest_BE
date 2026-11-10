package com.carenest.config;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ATT-06 (P-03 chưa chốt): giờ chốt dùng khi năm học chưa cấu hình giờ chốt; không hard-code trong service.
 * ATT-05: một đơn nghỉ tối đa leaveMaxDays ngày lịch (Cần chốt 03/11/2026: 30).
 */
@ConfigurationProperties("carenest.attendance")
public record AttendanceProperties(
        @DefaultValue("08:45") LocalTime defaultCutoff,
        @DefaultValue("30") int leaveMaxDays) {}
