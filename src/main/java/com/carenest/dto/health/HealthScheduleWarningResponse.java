package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthScheduleWarning;
import java.time.LocalDate;
import java.util.List;

/** Cảnh báo lịch trước khi lưu đợt/kỳ (user chốt 2026-10-31); rỗng = không có gì sát. Không chặn lưu. */
public record HealthScheduleWarningResponse(List<HealthScheduleWarningItem> warnings) {

    public record HealthScheduleWarningItem(
            HealthScheduleWarning code, Long campaignId, String name, HealthCheckKind kind, LocalDate startDate) {}
}
