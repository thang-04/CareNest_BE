package com.carenest.dto.organization;

import jakarta.validation.constraints.NotNull;

/** Cấu hình thời gian › Hạn gửi nhận xét tuần (OBS-29): số ngày học sau ngày học cuối của tuần, 0–5. */
public record WeeklyCommentDeadlineRequest(@NotNull Integer deadlineDays) {}
