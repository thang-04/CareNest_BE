package com.carenest.dto.organization;

import java.time.LocalDate;

public record SchoolYearResponse(Long id, String name, LocalDate startDate, LocalDate endDate) {}
