package com.carenest.utils.enums;

/**
 * Cách xác minh người đón (PCK-02): khớp ảnh phụ huynh trên hồ sơ trẻ, phụ huynh xác nhận qua điện thoại (trả trẻ) hoặc
 * từ chối / không liên lạc được (không trả).
 */
public enum PickupVerification {
    PHOTO_MATCH,
    PHONE_CONFIRMED,
    PHONE_DECLINED;

    /** Chỉ khớp ảnh hoặc phụ huynh xác nhận mới trả trẻ. */
    public PickupOutcome outcome() {
        return this == PHONE_DECLINED ? PickupOutcome.NOT_HANDED_OVER : PickupOutcome.HANDED_OVER;
    }
}
