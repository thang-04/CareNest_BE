package com.carenest.utils.enums;

/** Trạng thái tài liệu của lớp (EDU-54): Nháp, Chờ duyệt, Đã ban hành. */
public enum EduClassDocStatus {
    DRAFT,
    REVIEW,
    APPROVED;

    /** Còn sửa nội dung được (Nháp). */
    public boolean editable() {
        return this == DRAFT;
    }

    /** 3 trạng thái chuẩn hoá ra API / giao diện: DRAFT, REVIEW, APPROVED. */
    public EduClassDocStatus publicStatus() {
        return this;
    }
}
