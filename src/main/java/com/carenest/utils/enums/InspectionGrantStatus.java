package com.carenest.utils.enums;

/**
 * Trạng thái yêu cầu mở ghi biên bản ngày cũ (INS-05). {@code PENDING} chờ HT; {@code APPROVED} đang mở (tới hạn hoặc
 * tới khi ký); {@code CONSUMED} đã ký xong; {@code EXPIRED} quá hạn chưa ký; {@code REJECTED}/{@code REVOKED} không mở.
 */
public enum InspectionGrantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    REVOKED,
    CONSUMED,
    EXPIRED;

    /** Dòng đang chiếm phạm vi (một mẫu-ngày-điểm trường chỉ có một dòng như vậy). */
    public boolean isOpen() {
        return this == PENDING || this == APPROVED;
    }
}
