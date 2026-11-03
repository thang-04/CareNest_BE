package com.carenest.dto.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Ngày nghỉ của năm học (SCH-05). */
public final class SchoolHolidayDtos {
    private SchoolHolidayDtos() {}

    public record SchoolHolidayItem(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 100) String name) {}

    /** Thay toàn bộ danh sách ngày nghỉ của năm học. */
    public record SchoolHolidaysRequest(
            @NotNull @Size(max = 120) List<@NotNull @Valid SchoolHolidayItem> holidays) {}
}
