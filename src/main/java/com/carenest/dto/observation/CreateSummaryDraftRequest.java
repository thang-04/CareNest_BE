package com.carenest.dto.observation;

import com.carenest.utils.enums.SummaryPeriodType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateSummaryDraftRequest(
        @NotNull SummaryPeriodType periodType,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo) {}
