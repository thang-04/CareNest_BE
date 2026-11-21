package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Yêu cầu AI hỗ trợ soạn kế hoạch giáo dục (EDU-95); chỉ dữ liệu chương trình, không dữ liệu trẻ (AI-04). */
public final class EduAiRequest {
    private EduAiRequest() {}

    public record BranchNames(
            Long classId,
            Long ageGroupId,
            @NotNull @Size(min = 1, max = 40) List<@Valid Theme> themes,
            List<@Size(max = 200) String> taken) {}

    public record Theme(
            String id,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String festivals,
            @NotNull @Size(min = 1, max = 10) List<@Valid Week> weeks) {}

    public record Week(@NotBlank String start, Integer no) {}

    public record RewriteBranchName(
            Long classId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String themeName,
            Integer attempt) {}

    public record LessonSteps(
            @Size(max = 200) String subject,
            @Size(max = 10) List<@Size(max = 500) String> topics,
            @Size(max = 30) List<@Size(max = 500) String> goals) {}

    public record Activities(
            @Size(max = 200) String themeName,
            @NotNull @Size(min = 1, max = 60) List<@Valid Goal> goals) {}

    public record Goal(
            @NotBlank String id,
            Integer no,
            @Size(max = 1000) String text,
            @Size(max = 20) List<@Size(max = 1000) String> lines) {}

    /** Sửa theo yêu cầu: một câu giáo viên gõ. */
    public record EditDay(@NotBlank @Size(max = 500) String prompt) {}
}
