package com.carenest.dto.eduplan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản xem trước bảng mục tiêu đọc từ file (EDU-46): không lưu gì. Lĩnh vực theo tên cấu hình; dòng kind = sub | group |
 * goal; dấu tích chủ đề là tên viết tắt ở cột chủ đề của file (Web khớp với chủ đề của bản đang soạn).
 */
public record GoalFilePreviewResponse(
        List<Domain> domains,
        List<String> themeAbbrs,
        List<Warning> warnings,
        Metadata metadata,
        List<Theme> themes,
        List<Break> breaks) {

    public GoalFilePreviewResponse(List<Domain> domains, List<String> themeAbbrs, List<Warning> warnings) {
        this(domains, themeAbbrs, warnings, null, List.of(), List.of());
    }

    @Schema(name = "GoalFileMetadata")
    public record Metadata(String title, String schoolYear, Integer ageFromMonths, Integer ageToMonths) {}

    @Schema(name = "GoalFileTheme")
    public record Theme(String name, String abbr, String festivals, LocalDate start, LocalDate end, String note) {}

    @Schema(name = "GoalFileBreak")
    public record Break(String name, LocalDate start, LocalDate end, String note) {}

    public record Domain(String name, List<Row> rows) {}

    public record Row(String kind, String text, List<Content> contents) {}

    public record Content(String text, List<String> themes) {}

    /** row: số dòng trong bảng/trang tính/CSV, đếm từ 1. */
    public record Warning(int row, String message) {}
}
