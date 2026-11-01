package com.carenest.utils.enums;

/** Trạng thái tài liệu của lớp (EDU-18): chờ duyệt theo vai trò ký đang tới lượt. */
public enum EduClassDocStatus {
    DRAFT,
    PENDING_TL,
    PENDING_VP,
    PENDING_HT,
    REJECTED,
    APPROVED;

    /** Còn sửa nội dung được (Nháp hoặc Bị trả lại). */
    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }
}
