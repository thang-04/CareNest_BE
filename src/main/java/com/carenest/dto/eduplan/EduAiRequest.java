package com.carenest.dto.eduplan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Yêu cầu AI hỗ trợ soạn kế hoạch giáo dục (EDU-95); chỉ dữ liệu chương trình, không dữ liệu trẻ (AI-04). */
public final class EduAiRequest {
    private EduAiRequest() {}

    public record Theme(
            String id,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String festivals,
            @NotNull @Size(min = 1, max = 10) List<@Valid Week> weeks) {}

    public record Week(@NotBlank String start, Integer no) {}

    /**
     * activity: tên hoạt động của giáo án ngày (vd. "Hoạt động ngoài trời"); steps: tên các bước của mục đó — có steps thì AI
     * trả đúng từng bước (rows), không thì theo khung a/b/c của hoạt động học.
     */
    public record LessonSteps(
            @Size(max = 200) String subject,
            @Size(max = 10) List<@Size(max = 500) String> topics,
            @Size(max = 30) List<@Size(max = 500) String> goals,
            @Size(max = 200) String activity,
            @Size(max = 10) List<@Size(max = 200) String> steps) {

        public LessonSteps(String subject, List<String> topics, List<String> goals) {
            this(subject, topics, goals, null, null);
        }
    }

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

    /** EDU-103: yêu cầu tổ trưởng gõ cho từng chủ đề (không bắt buộc); nội dung BE tự đọc từ kế hoạch. */
    @Schema(name = "EduAiThemeContentMapRequest")
    public record ThemeContentMap(@Size(max = 60) List<@Valid ThemeRequest> themes) {}

    @Schema(name = "EduAiThemeRequest")
    public record ThemeRequest(
            @NotNull Long themeId, @Size(max = 500) String request) {}

    /** EDU-103: số đếm mỗi lần Áp dụng, chỉ ghi log để theo dõi tỉ lệ giữ. */
    @Schema(name = "EduAiFeedbackRequest")
    public record Feedback(
            @NotBlank @Pattern(regexp = "TEAM|SCHOOL_YEAR") String kind,
            @NotNull @Min(0) Integer suggested,
            @NotNull @Min(0) Integer kept,
            @Min(0) Integer failedDomains,
            @Min(0) Long waitMs) {}
}
