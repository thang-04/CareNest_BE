package com.carenest.utils.enums;

/** Trạng thái phiếu theo dõi, tính từ dòng thời gian: chờ trao đổi phụ huynh, đã trao đổi, đã có kết quả chuyên khoa, đã đóng. */
public enum HealthFollowUpStatus {
    WAITING_PARENT_CONTACT,
    PARENT_CONTACTED,
    EXTERNAL_RESULT_RECORDED,
    CLOSED
}
