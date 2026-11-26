package com.carenest.dto.attendance;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Sửa nhầm công tắc tham gia ăn sau giờ chốt, chỉ trong ngày hiện tại. */
public record CorrectMealParticipationRequest(@NotNull LocalDate date, boolean participates) {}
