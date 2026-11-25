package com.carenest.dto.meals;

import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.Instant;
import java.time.LocalDate;

/** Một yêu cầu mở ghi mẫu ngày cũ (INS-05) cho hàng chờ của HT và kết quả thao tác. */
public record InspectionApprovalResponse(
        Long id,
        Long campusId,
        LocalDate date,
        InspectionTab tab,
        InspectionGrantStatus status,
        boolean selfOpened,
        String reason,
        String requestedByName,
        Instant requestedAt,
        String decidedByName,
        Instant decidedAt,
        String decisionNote,
        Instant expiresAt) {}
