package com.carenest.dto.child;

import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;

/** Năm học trẻ có học lớp, cho bộ chọn năm App phụ huynh; {@code className} = lớp của trẻ trong năm đó. */
public record ChildSchoolYearResponse(
        Long schoolYearId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        SchoolYearStatus status,
        String className) {}
