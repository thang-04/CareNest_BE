package com.carenest.dto.eduplan;

import com.carenest.utils.enums.EduPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết kế hoạch năm học: bảng mục tiêu theo lĩnh vực, chủ đề, đợt nghỉ, lịch sử và quyền của người xem.
 * editing = người đang giữ khóa "Đang sửa" và lúc bắt đầu (EDU-10); null khi không ai sửa.
 */
public record YearPlanDetailResponse(
        Long id,
        String code,
        Long schoolYearId,
        String schoolYearName,
        Long ageGroupId,
        String ageGroupName,
        String title,
        String description,
        EduPlanStatus status,
        PlanActorResponse owner,
        PlanActorResponse sentBy,
        PlanActorResponse issued,
        PlanActorResponse editing,
        Instant createdAt,
        Instant updatedAt,
        List<Domain> domains,
        List<Theme> themes,
        List<Break> breaks,
        List<History> history,
        String lastReturnNote,
        Permissions permissions) {

    public record Domain(Long domainId, String name, String label, List<Row> rows) {}

    /** goalNo chỉ có ở dòng kind = goal (MT n, EDU-09). */
    public record Row(Long id, String kind, String text, Short goalNo, List<Content> contents) {}

    public record Content(Long id, String text, List<Long> themeIds) {}

    public record Theme(
            Long id, String name, String abbr, LocalDate startDate, LocalDate endDate, String festivals, String note) {}

    public record Break(Long id, String label, LocalDate startDate, LocalDate endDate) {}

    public record History(
            String action,
            String label,
            String actorName,
            String actorRoleLabel,
            String tone,
            String note,
            Instant at) {}

    public record Permissions(
            boolean canEdit, boolean canSubmit, boolean canIssue, boolean canReturn, boolean canDelete) {}
}
