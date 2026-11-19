package com.carenest.dto.organization;

import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;

public record SchoolYearDetailResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate semester1Start,
        LocalDate semester1End,
        LocalDate semester2Start,
        LocalDate semester2End,
        SchoolYearStatus status,
        long classCount,
        Instant activatedAt,
        Long activatedBy,
        Instant closedAt,
        Long closedBy,
        Instant createdAt,
        Instant updatedAt,
        LocalDate prepDate,
        boolean preparing) {

    /** Không kèm ngày mở chuẩn bị (CFG-YR-05): cho nơi dựng sẵn dữ liệu năm học cũ (test, module khác). */
    public SchoolYearDetailResponse(
            Long id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate semester1Start,
            LocalDate semester1End,
            LocalDate semester2Start,
            LocalDate semester2End,
            SchoolYearStatus status,
            long classCount,
            Instant activatedAt,
            Long activatedBy,
            Instant closedAt,
            Long closedBy,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                name,
                startDate,
                endDate,
                semester1Start,
                semester1End,
                semester2Start,
                semester2End,
                status,
                classCount,
                activatedAt,
                activatedBy,
                closedAt,
                closedBy,
                createdAt,
                updatedAt,
                null,
                false);
    }
}
