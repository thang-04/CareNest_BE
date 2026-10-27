package com.carenest.dto.eduplan;

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
        @Valid List<Break> breaks) {

    public record Domain(@NotNull Long domainId, @Valid List<Row> rows) {}

    public record Row(
            Long id,
            @NotBlank @Pattern(regexp = "sub|group|goal") String kind,
            String text,
            @Valid List<Content> contents) {}

    /** themeKeys trỏ tới {@link Theme#key()} của cùng request, nên tích được cả chủ đề vừa thêm chưa có id. */
    public record Content(Long id, String text, List<String> themeKeys) {}

    public record Theme(
            @NotBlank @Size(max = 60) String key,
            Long id,
            @Size(max = 150) String name,
            @Size(max = 20) String abbr,
            LocalDate startDate,
            LocalDate endDate,
            String festivals,
            String note) {}

    public record Break(Long id, @Size(max = 150) String label, LocalDate startDate, LocalDate endDate) {}
}
