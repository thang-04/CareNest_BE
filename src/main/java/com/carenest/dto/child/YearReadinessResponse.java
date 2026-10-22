package com.carenest.dto.child;

import java.time.LocalDate;
import java.util.List;

/**
 * Các bước chuẩn bị năm học (key theo FE: info, ageGroups, classes, rooms, vicePrincipals, teamLeaders, teachers,
 * placement, cutoff); bước required chưa xong hoặc chưa tới ngày mở kích hoạt ⇒ có trong blockers.
 */
public record YearReadinessResponse(
        Long yearId,
        List<Step> steps,
        LocalDate openFrom,
        PlacementBoardResponse.YearBrief sourceYear,
        Impact impact,
        List<String> blockers,
        boolean canActivate) {

    public record Step(String key, String label, boolean required, boolean ok, String detail) {}

    public record Impact(long move, long leave) {}
}
