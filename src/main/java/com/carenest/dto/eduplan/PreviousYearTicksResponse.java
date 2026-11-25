package com.carenest.dto.eduplan;

import java.util.List;

/** EDU-104: tick của kế hoạch tổ đã ban hành năm học liền trước; source null kèm reason khi không có nguồn. */
public record PreviousYearTicksResponse(Source source, String reason, List<Theme> themes, List<Tick> ticks) {

    public static final String NO_PREVIOUS_YEAR = "NO_PREVIOUS_YEAR";
    public static final String NO_APPROVED_PLAN = "NO_APPROVED_PLAN";

    public static PreviousYearTicksResponse none(String reason) {
        return new PreviousYearTicksResponse(null, reason, List.of(), List.of());
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "PreviousYearTicksSource")
    public record Source(Long planId, String code, Long schoolYearId, String schoolYearName, int versionNo) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "PreviousYearTicksTheme")
    public record Theme(Long id, String name) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "PreviousYearTicksTick")
    public record Tick(Long domainId, String text, Long themeId) {}
}
