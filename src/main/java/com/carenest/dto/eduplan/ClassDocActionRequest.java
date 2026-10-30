package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Gửi / duyệt / trả lại tài liệu của lớp (EDU-18). action: submit | approve | reject; trả lại bắt buộc ghi chú. */
public record ClassDocActionRequest(
        @NotBlank String action, @Size(max = 2000) String note) {}
