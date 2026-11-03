package com.carenest.utils.enums;

/** Sự kiện trên dòng thời gian phiếu theo dõi: mở, thêm lần khám, trao đổi phụ huynh, kết quả chuyên khoa, đóng. */
public enum HealthFollowUpAction {
    OPENED,
    MEASUREMENT_ADDED,
    PARENT_CONTACTED,
    EXTERNAL_RESULT,
    CLOSED
}
