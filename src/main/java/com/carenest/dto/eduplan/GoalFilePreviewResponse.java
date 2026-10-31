package com.carenest.dto.eduplan;

import java.util.List;

/**
 * Bản xem trước bảng mục tiêu đọc từ file (EDU-46): không lưu gì. Lĩnh vực theo tên cấu hình; dòng kind = sub | group |
 * goal; dấu tích chủ đề là tên viết tắt ở cột chủ đề của file (Web khớp với chủ đề của bản đang soạn).
 */
public record GoalFilePreviewResponse(List<Domain> domains, List<String> themeAbbrs, List<Warning> warnings) {

    public record Domain(String name, List<Row> rows) {}

    public record Row(String kind, String text, List<Content> contents) {}

    public record Content(String text, List<String> themes) {}

    /** row: số dòng trong bảng/trang tính/CSV, đếm từ 1. */
    public record Warning(int row, String message) {}
}
