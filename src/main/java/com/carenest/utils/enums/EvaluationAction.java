package com.carenest.utils.enums;

/** Vết xử lý đánh giá tháng / cuối năm (giữ tên khóa FE đang dùng). */
public enum EvaluationAction {
    AI_DRAFTED,
    AI_REDRAFTED,
    // AI-03: nháp hệ thống tạo từ mẫu câu (không qua AI)
    TEMPLATE_DRAFTED,
    EDITED,
    CONFIRMED
}
