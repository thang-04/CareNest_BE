package com.carenest.dto.child;

import com.carenest.utils.enums.ChildStatus;

/** Trẻ đang liên kết với một tài khoản phụ huynh, kèm lớp và điểm trường hiện tại (để lọc theo phạm vi người hỏi). */
public record GuardianChildSummary(
        Long id, String code, String fullName, ChildStatus status, String className, Long campusId) {}
