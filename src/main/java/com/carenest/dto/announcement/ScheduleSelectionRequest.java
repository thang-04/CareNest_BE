package com.carenest.dto.announcement;

import java.util.List;

/** Các lịch gửi được tick để xóa (PAR-10). */
public record ScheduleSelectionRequest(List<Long> ids) {}
