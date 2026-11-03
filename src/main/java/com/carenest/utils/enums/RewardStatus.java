package com.carenest.utils.enums;

/** Danh sách khen thưởng của lớp: GV lập → PHT xem xét → HT phê duyệt; PHT / HT trả lại thì GV sửa và gửi lại. */
public enum RewardStatus {
    DRAFT,
    PENDING_VP,
    PENDING_PRINCIPAL,
    APPROVED,
    RETURNED
}
