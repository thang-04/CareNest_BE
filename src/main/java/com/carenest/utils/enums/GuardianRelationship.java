package com.carenest.utils.enums;

/**
 * Quan hệ phụ huynh–trẻ, dùng chung cho hồ sơ trẻ và liên kết tài khoản (AUTH-18, D-5a B; CHECK V10).
 * {@link #OTHER} chỉ còn ở dữ liệu trước V10.
 */
public enum GuardianRelationship {
    FATHER,
    MOTHER,
    GRANDFATHER,
    GRANDMOTHER,
    SIBLING,
    GUARDIAN,
    OTHER
}
