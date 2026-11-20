package com.carenest.service.eduplan.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hạn gói học kỳ / hồ sơ chủ đề (spec 003, data-model "Hạn"): tính khi đọc, không lưu.
 * Hạn = ngày bắt đầu học kỳ / chủ đề; mở hạn (HT/PHT) thay bằng hạn mới.
 */
final class EduDeadline {
    static final String NORMAL = "NORMAL";
    static final String NEAR = "NEAR";
    static final String OVERDUE = "OVERDUE";
    static final String ON_TIME = "ON_TIME";
    static final String LATE = "LATE";
    static final int NEAR_DAYS = 7;

    /** graceTo: hạn gửi lại sau lần trả lại trễ đầu tiên (không phải mở hạn của HT, không đổi nhãn Ban hành trễ). */
    record Info(LocalDate deadline, LocalDate extendedTo, LocalDate graceTo, Long daysLeft, String state) {
        LocalDate effective() {
            return later(extendedTo != null ? extendedTo : deadline, graceTo);
        }
    }

    /** Sự kiện trên tài liệu theo thời gian: trả lại (reject) hoặc HT mở hạn tới extendTo. */
    record Event(LocalDate date, boolean reject, LocalDate extendTo) {}

    private EduDeadline() {}

    /**
     * Trả lại sau hạn (Tiền 18/11/2026): lần trả lại đầu tiên xảy ra khi đã quá hạn hiệu lực lúc đó (hạn gốc hoặc hạn HT
     * mở) cho người soạn gửi lại hết {@code days} ngày lịch sau ngày trả lại; mỗi tài liệu chỉ một lần.
     */
    static LocalDate graceTo(LocalDate deadline, List<Event> events, int days) {
        if (deadline == null) return null;
        LocalDate current = deadline;
        for (Event e : events) {
            if (!e.reject()) {
                if (e.extendTo() != null) current = e.extendTo();
            } else if (!e.date().isBefore(current)) return e.date().plusDays(days + 1L);
        }
        return null;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        if (a == null) return b;
        return b == null || a.isAfter(b) ? a : b;
    }

    static Info of(
            LocalDate deadline,
            LocalDate extendedTo,
            LocalDate today,
            boolean approved,
            LocalDate firstRelease,
            LocalDate classOpened) {
        return of(deadline, extendedTo, null, today, approved, firstRelease, classOpened);
    }

    /**
     * @param approved trạng thái hiện tại là Đã ban hành
     * @param firstRelease ngày ban hành lần đầu (null nếu chưa từng)
     * @param classOpened ngày mở lớp: hạn trước ngày này không tính quá hạn (lớp mở giữa năm)
     */
    static Info of(
            LocalDate deadline,
            LocalDate extendedTo,
            LocalDate graceTo,
            LocalDate today,
            boolean approved,
            LocalDate firstRelease,
            LocalDate classOpened) {
        if (deadline == null) return new Info(null, null, null, null, NORMAL);
        LocalDate official = extendedTo != null ? extendedTo : deadline;
        LocalDate effective = later(official, graceTo);
        long daysLeft = ChronoUnit.DAYS.between(today, effective);
        String state;
        if (approved) state = firstRelease == null || firstRelease.isBefore(official) ? ON_TIME : LATE;
        else if (classOpened != null && effective.isBefore(classOpened)) state = NORMAL;
        else if (daysLeft <= 0) state = OVERDUE;
        else if (daysLeft <= NEAR_DAYS) state = NEAR;
        else state = NORMAL;
        return new Info(deadline, extendedTo, graceTo, daysLeft, state);
    }
}
