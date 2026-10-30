package com.carenest.dto.organization;

import com.carenest.utils.enums.YearCopyPart;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** parts rỗng/null: service báo lỗi YEAR_COPY_PARTS_REQUIRED (mã riêng để FE hiện lỗi đỏ). */
public record YearCopyRequest(@NotNull Long sourceYearId, Set<YearCopyPart> parts) {}
