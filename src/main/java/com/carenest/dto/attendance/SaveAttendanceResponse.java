package com.carenest.dto.attendance;

import java.time.Instant;

/** count = số trẻ gửi lên; changed = số bản ghi mới hoặc đổi trạng thái/bữa. */
public record SaveAttendanceResponse(Instant savedAt, int count, int changed) {}
