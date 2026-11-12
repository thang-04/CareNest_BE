package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Gửi / duyệt / trả lại gói học kỳ hoặc hồ sơ chủ đề (EDU-18). action: submit | approve | reject | extend; trả lại và
 * mở hạn bắt buộc ghi chú; mở hạn cần deadline mới (spec 003 FR-004a).
 */
public record ClassDocActionRequest(
        @NotBlank String action, @Size(max = 2000) String note, java.time.LocalDate deadline) {

    public ClassDocActionRequest(String action, String note) {
        this(action, note, null);
    }
}
