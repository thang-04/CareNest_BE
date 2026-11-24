package com.carenest.service.child;

import java.time.LocalDate;

/**
 * Dữ liệu trong ngày chỉ chặn cho nghỉ học từ hôm nay (G94-180/CHD-03, user chốt 2026-11-25), không chặn chuyển lớp:
 * module giữ dữ liệu tự kiểm. Dữ liệu chặn cả hai việc dùng {@link ChildDayUsageCheck}.
 */
public interface ChildLeaveDataCheck {
    boolean hasData(Long childId, LocalDate date);
}
