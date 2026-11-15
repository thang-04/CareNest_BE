package com.carenest.dto.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Cả văn bản kế hoạch năm học, gửi mỗi lần bấm Lưu. Dòng/chủ đề có id thì cập nhật, không có id thì tạo mới,
 * id cũ không còn trong request thì xóa. schoolYearId, ageGroupId chỉ dùng khi tạo.
 */
public record YearPlanRequest(
        Long schoolYearId,
        Long ageGroupId,
        @NotNull @Size(max = 150) String title,
        @Size(max = 500) String description,
        @Valid List<Domain> domains,
        @Valid List<Theme> themes,
        @Valid List<Break> breaks,
        com.carenest.utils.enums.EduPlanLevel planLevel,
        Long curriculumSourcePlanId,
        @Valid List<BranchTemplate> branchTemplates,
        com.carenest.utils.enums.EduPlanLevel planType) {
    public YearPlanRequest(
            Long schoolYearId,
            Long ageGroupId,
            @NotNull @Size(max = 150) String title,
            @Size(max = 500) String description,
            @Valid List<Domain> domains,
            @Valid List<Theme> themes,
            @Valid List<Break> breaks) {
        this(
                schoolYearId,
                ageGroupId,
                title,
                description,
                domains,
                themes,
                breaks,
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                null,
                List.of());
    }

    public YearPlanRequest(
            Long schoolYearId,
            Long ageGroupId,
            String title,
            String description,
            List<Domain> domains,
            List<Theme> themes,
            List<Break> breaks,
            com.carenest.utils.enums.EduPlanLevel planLevel,
            Long curriculumSourcePlanId,
            List<BranchTemplate> branchTemplates) {
        this(
                schoolYearId,
                ageGroupId,
                title,
                description,
                domains,
                themes,
                breaks,
                planLevel,
                curriculumSourcePlanId,
                branchTemplates,
                null);
    }

    public YearPlanRequest {
        if (planType != null && planLevel != null && planType != planLevel) {
            throw new IllegalArgumentException("planType and planLevel must agree");
        }
        planLevel = planType != null
                ? planType
                : planLevel == null ? com.carenest.utils.enums.EduPlanLevel.TEAM : planLevel;
        planType = planLevel;
        branchTemplates = branchTemplates == null ? List.of() : List.copyOf(branchTemplates);
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanBranchTemplateRequest")
    public record BranchTemplate(
            String key,
            Long id,
            @NotBlank String themeKey,
            @Size(max = 150) String name,
            LocalDate startDate,
            LocalDate endDate) {}

    @Schema(name = "YearPlanDomainRequest")
    public record Domain(@NotNull Long domainId, @Valid List<Row> rows) {}

    /**
     * Liên kết danh mục Chương trình của Bộ (EDU-28), mỗi tầng một field: curriculumSubDomainId cho dòng sub,
     * curriculumExpectedResultId cho dòng group, curriculumGoalId cho dòng goal; field của tầng khác bị bỏ qua.
     */
    @Schema(name = "YearPlanGoalRowRequest")
    public record Row(
            Long id,
            @NotBlank @Pattern(regexp = "sub|group|goal") String kind,
            String text,
            @Valid List<Content> contents,
            Long curriculumGoalId,
            Long curriculumSubDomainId,
            Long curriculumExpectedResultId,
            @Size(max = 100) String displayLabel) {
        public Row(
                Long id,
                String kind,
                String text,
                List<Content> contents,
                Long curriculumGoalId,
                Long curriculumSubDomainId,
                Long curriculumExpectedResultId) {
            this(id, kind, text, contents, curriculumGoalId, curriculumSubDomainId, curriculumExpectedResultId, null);
        }

        public Row(Long id, String kind, String text, List<Content> contents) {
            this(id, kind, text, contents, null, null, null);
        }

        public Row(Long id, String kind, String text, List<Content> contents, Long curriculumGoalId) {
            this(id, kind, text, contents, curriculumGoalId, null, null);
        }

        public Long curriculumId(EduGoalRowKind rowKind) {
            return switch (rowKind) {
                case SUB -> curriculumSubDomainId;
                case GROUP -> curriculumExpectedResultId;
                case GOAL -> curriculumGoalId;
            };
        }
    }

    /** themeKeys trỏ tới {@link Theme#key()} của cùng request, nên tích được cả chủ đề vừa thêm chưa có id. */
    @Schema(name = "YearPlanGoalContentRequest")
    public record Content(Long id, String text, List<String> themeKeys) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanThemeRequest")
    public record Theme(
            @NotBlank @Size(max = 60) String key,
            Long id,
            @Size(max = 150) String name,
            @Size(max = 20) String abbr,
            LocalDate startDate,
            LocalDate endDate,
            String festivals,
            String note) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "YearPlanBreakRequest")
    public record Break(Long id, @Size(max = 150) String label, LocalDate startDate, LocalDate endDate) {}
}
