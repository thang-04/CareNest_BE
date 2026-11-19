package com.carenest.utils.enums;

public enum RewardAction {
    CREATED,
    UPDATED,
    SUBMITTED,
    RESUBMITTED,
    FORWARDED,
    RETURNED,
    APPROVED,
    /** HT duyệt thẳng khi PHT chưa xét (Cần chốt 11/11/2026). */
    DIRECT_APPROVED
}
