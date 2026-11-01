package com.carenest.dto.organization;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** Giờ chốt dạng HH:mm, áp dụng từ ngày effectiveFrom (trong năm học). */
public record CutoffRequest(
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String note) {}
