package com.carenest.dto.organization;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** classIds rỗng/null = sao chép mọi lớp của năm nguồn. */
public record CopyClassesRequest(@NotNull Long sourceYearId, List<Long> classIds) {}
