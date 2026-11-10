package com.carenest.utils.enums;

/** Trạng thái kế hoạch năm học (EDU-03..05, EDU-10): nháp, đang sửa (khóa), chờ Phó HT duyệt, bị trả lại, đã ban hành. */
public enum EduPlanStatus {
    DRAFT,
    EDITING,
    PENDING_VP,
    REJECTED,
    APPROVED
}
