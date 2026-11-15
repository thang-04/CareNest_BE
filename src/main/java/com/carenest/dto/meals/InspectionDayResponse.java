package com.carenest.dto.meals;

import java.time.LocalDate;
import java.util.List;

/** Các biên bản kiểm thực của một điểm trường–ngày. {@code canEdit}: người xem ghi, ký, sửa lại được (INS-01). */
public record InspectionDayResponse(
        Long campusId, LocalDate date, boolean canEdit, List<InspectionSheetResponse> sheets) {}
