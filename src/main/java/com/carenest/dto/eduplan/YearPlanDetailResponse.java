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
        EduReviewStateResponse review,
        Permissions permissions,
        long revision,
        Instant editExpiresAt,
        int versionNo,
        Long sourcePlanId,
        boolean current,
        List<VersionSummary> versions,
        com.carenest.utils.enums.EduPlanLevel planLevel,
        Long curriculumSourcePlanId,
        String sourceMode,
        @com.fasterxml.jackson.annotation.JsonRawValue String durationPolicySnapshot,
        List<BranchTemplate> branchTemplates,
        boolean canEditCurriculum,
        boolean canAssignContents,
        boolean canEditBranchTemplates,
        List<String> remapWarnings,
        Long previousSourcePlanId,
        List<ReviewItem> reviewItems,
        List<NewItem> newItems) {

    @com.fasterxml.jackson.annotation.JsonProperty
    public com.carenest.utils.enums.EduPlanLevel planType() { return planLevel; }

    @com.fasterxml.jackson.annotation.JsonProperty
    public String planTypeLabel() {
        return planLevel == com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR ? "Kế hoạch năm học" : "Kế hoạch tổ";
    }

    @com.fasterxml.jackson.annotation.JsonProperty
    public Instant sentAt() { return sentBy == null ? null : sentBy.at(); }

    @com.fasterxml.jackson.annotation.JsonProperty
    public long contentRevision() { return revision; }

    public record ReviewItem(Long reviewItemId, String itemKind, Long itemId, Long sourcePlanId,
            String reason, boolean resolved, Long resolvedBy, Instant resolvedAt) {}
    public record NewItem(String itemKind, Long itemId) {}
    public YearPlanDetailResponse(
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
            EduReviewStateResponse review,
            Permissions permissions,
            long revision,
            Instant editExpiresAt,
            int versionNo,
            Long sourcePlanId,
            boolean current,
            List<VersionSummary> versions,
            com.carenest.utils.enums.EduPlanLevel planLevel,
            Long curriculumSourcePlanId,
            String sourceMode,
            String durationPolicySnapshot,
            List<BranchTemplate> branchTemplates,
            boolean canEditCurriculum,
            boolean canAssignContents,
            boolean canEditBranchTemplates,
            List<String> remapWarnings) {
        this(id, code, schoolYearId, schoolYearName, ageGroupId, ageGroupName, title, description, status, owner, sentBy, issued, editing, createdAt, updatedAt, domains, themes, breaks, history, lastReturnNote, review, permissions, revision, editExpiresAt, versionNo, sourcePlanId, current, versions, planLevel, curriculumSourcePlanId, sourceMode, durationPolicySnapshot, branchTemplates, canEditCurriculum, canAssignContents, canEditBranchTemplates, remapWarnings, null, List.of(), List.of());
    }
    public YearPlanDetailResponse(
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
            EduReviewStateResponse review,
            Permissions permissions,
            long revision,
            Instant editExpiresAt,
            int versionNo,
            Long sourcePlanId,
            boolean current,
            List<VersionSummary> versions) {
        this(
                id,
                code,
                schoolYearId,
                schoolYearName,
                ageGroupId,
                ageGroupName,
                title,
                description,
                status,
                owner,
                sentBy,
                issued,
                editing,
                createdAt,
                updatedAt,
                domains,
                themes,
                breaks,
                history,
                lastReturnNote,
                review,
                permissions,
                revision,
                editExpiresAt,
                versionNo,
                sourcePlanId,
                current,
                versions,
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                null,
                "LEGACY",
                "{\"mode\":\"LEGACY_UNBOUNDED\"}",
                List.of(),
                false,
                false,
                false,
                List.of());
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanBranchTemplateResponse")
    public record BranchTemplate(
            Long id,
            Long themeId,
            Long lineageId,
            Long sourceTemplateId,
            String name,
            LocalDate startDate,
            LocalDate endDate) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanDomainResponse")
    public record Domain(Long domainId, String name, String label, List<Row> rows) {}

    /** goalNo chỉ có ở dòng kind = goal (MT n, EDU-09). */
    /**
     * Liên kết danh mục của Bộ (EDU-28): curriculumGoalId/GoalText cho mục tiêu, curriculumSubDomainId hoặc
     * curriculumExpectedResultId + curriculumText (câu gốc) cho lĩnh vực con/nhóm; curriculumSectionRef chỉ mục tiêu có.
     */
    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanGoalRowResponse")
    public record Row(
            Long id,
            String kind,
            String text,
            Short goalNo,
            List<Content> contents,
            Long curriculumGoalId,
            String curriculumGoalText,
            String curriculumSectionRef,
            Long curriculumSubDomainId,
            Long curriculumExpectedResultId,
            String curriculumText,
            String displayLabel) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanGoalContentResponse")
    public record Content(Long id, String text, List<Long> themeIds) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanThemeResponse")
    public record Theme(
            Long id, String name, String abbr, LocalDate startDate, LocalDate endDate, String festivals, String note) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanBreakResponse")
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
            boolean canEdit,
            boolean canSubmit,
            boolean canApprove,
            boolean canIssue,
            boolean canReturn,
            boolean canDelete,
            boolean canCreateVersion) {}

    /** Một phiên bản trong chuỗi (spec 004); current = phiên bản ban hành mới nhất. */
    public record VersionSummary(
            Long id,
            int versionNo,
            String code,
            EduPlanStatus status,
            String ownerName,
            Instant createdAt,
            String issuedByName,
            Instant issuedAt,
            boolean current) {}
}
