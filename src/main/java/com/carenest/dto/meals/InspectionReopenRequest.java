package com.carenest.dto.meals;

import jakarta.validation.constraints.Size;

/** Sửa lại biên bản đã ký (INS-04): lý do bắt buộc, kiểm độ dài ở service để trả mã lỗi riêng. */
public record InspectionReopenRequest(@Size(max = 300) String reason) {}
