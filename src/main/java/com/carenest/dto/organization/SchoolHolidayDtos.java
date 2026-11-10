package com.carenest.dto.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Đợt nghỉ của năm học (SCH-05): từ ngày – đến ngày, tên đợt nghỉ. */
public final class SchoolHolidayDtos {
    private SchoolHolidayDtos() {}

    public record SchoolHolidayItem(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 100) String name) {}

    /** Thay toàn bộ danh sách đợt nghỉ của năm học. */
    public record SchoolHolidaysRequest(
            @NotNull @Size(max = 60) List<@NotNull @Valid SchoolHolidayItem> holidays) {}
}
