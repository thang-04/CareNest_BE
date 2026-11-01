package com.carenest.dto.organization;

import java.time.Instant;
import java.time.LocalDate;

/** Một lần phân công (giáo viên lớp / tổ trưởng); current = còn hiệu lực từ hôm nay (validTo null hoặc chưa qua). */
public record AssignmentHistoryResponse(
        Long id,
        Long userId,
        String fullName,
        boolean homeroom,
        LocalDate validFrom,
        LocalDate validTo,
        boolean current,
        Long assignedBy,
        String assignedByName,
        Instant assignedAt) {}
