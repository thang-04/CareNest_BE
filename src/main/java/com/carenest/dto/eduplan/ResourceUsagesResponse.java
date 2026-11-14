package com.carenest.dto.eduplan;

import java.time.LocalDate;
import java.util.List;

/** EDU-85: nơi đang dùng + có bị khóa không; {@code deletableFrom} null khi còn giáo án Chờ duyệt hoặc không khóa. */
public record ResourceUsagesResponse(List<ResourceUsageResponse> rows, boolean locked, LocalDate deletableFrom) {}
