package com.carenest.dto.account;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Ngày cuối còn hiệu lực của phân công. */
public record EndStaffAssignmentRequest(@NotNull LocalDate validTo) {}
