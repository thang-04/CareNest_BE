package com.carenest.utils.enums;

/** Đơn nghỉ: cả ngày hoặc một buổi (ATT-12, Cần chốt 18/11/2026). */
public enum LeavePart {
    FULL_DAY,
    MORNING,
    AFTERNOON;

    /** Buổi nghỉ của đơn nửa buổi; đơn cả ngày trả null. */
    public DayPart dayPart() {
        return switch (this) {
            case MORNING -> DayPart.MORNING;
            case AFTERNOON -> DayPart.AFTERNOON;
            case FULL_DAY -> null;
        };
    }
}
