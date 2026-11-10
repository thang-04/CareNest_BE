package com.carenest.utils.enums;

/** Loại tài liệu tham khảo cho RAG; module AI lọc theo loại trước khi tìm vector. */
public enum AiDocumentType {
    NUTRITION_STANDARD,
    FOOD_SAFETY_GUIDANCE,
    FOOD_SAFETY_ALERT,
    HEALTH_MONITORING_GUIDANCE,
    /** Chuẩn/chỉ số phát triển trẻ theo độ tuổi (vd. Bộ chuẩn phát triển trẻ 5 tuổi). */
    CHILD_DEVELOPMENT_STANDARD,
    SCHOOL_POLICY,
    SCHOOL_SOP,
    APPROVED_MENU,
    MENU_GUIDELINE,
    OTHER_REFERENCE
}
