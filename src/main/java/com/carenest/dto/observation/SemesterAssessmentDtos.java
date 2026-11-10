package com.carenest.dto.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class SemesterAssessmentDtos {
    private SemesterAssessmentDtos() {}

    @Schema(name = "SemesterAssessmentRequest")
    public record Request(
            @NotNull Long classId,
            @Min(1) @Max(2) int semester,
            @Size(max = 1000) String note) {}

    // metActivities / assessedActivities giữ tên cũ (không đổi contract): nay là số chủ đề Đạt / chủ đề có kết quả
    // (OBS-25)
    @Schema(name = "SemesterAssessmentGoal")
    public record Goal(
            Long goalId, Short goalNo, String text, Boolean met, int metActivities, int assessedActivities) {}

    @Schema(name = "SemesterAssessmentDomain")
    public record Domain(
            Long domainId,
            String name,
            List<Goal> goals,
            int metCount,
            int notMetCount,
            int missingCount,
            Boolean met) {}

    @Schema(name = "SemesterAssessmentChild")
    public record Child(Long childId, String fullName, List<Domain> domains) {}

    @Schema(name = "SemesterAssessmentResponse")
    public record Response(
            Long classId,
            String className,
            Long schoolYearId,
            String yearName,
            int semester,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String note,
            List<Domain> domains,
            List<Child> rows,
            boolean canEdit,
            boolean canConfirm,
            Instant confirmedAt,
            String confirmedByName,
            // Họ tên in ở chỗ ký: GV chủ nhiệm, tổ trưởng chuyên môn, PHT (như bản in cuối chủ đề)
            ThemeEvaluationDtos.ThemeSigners signers) {}
}
