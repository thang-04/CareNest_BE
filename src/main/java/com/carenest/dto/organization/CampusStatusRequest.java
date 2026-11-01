package com.carenest.dto.organization;

import com.carenest.utils.enums.CampusStatus;
import jakarta.validation.constraints.NotNull;

/** Ngừng hoạt động / hoạt động lại điểm trường; xóa dùng DELETE. */
public record CampusStatusRequest(@NotNull CampusStatus status) {}
