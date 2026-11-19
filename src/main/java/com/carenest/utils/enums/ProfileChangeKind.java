package com.carenest.utils.enums;

/** Loại yêu cầu sửa hồ sơ trẻ của GV chờ duyệt (CHD-11, V153). */
public enum ProfileChangeKind {
    /** Thông tin trẻ và người giám hộ (`PUT /children/{id}`). */
    PROFILE,
    /** Khai báo sức khỏe, dị ứng (`PUT /children/{id}/health-declaration`). */
    HEALTH_DECLARATION
}
