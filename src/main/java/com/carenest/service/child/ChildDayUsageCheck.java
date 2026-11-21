package com.carenest.service.child;

import java.time.LocalDate;

/** Module giữ dữ liệu ngày tự kiểm trước khi trẻ chuyển lớp hoặc nghỉ học. */
public interface ChildDayUsageCheck {
    boolean hasData(Long childId, LocalDate date);
}
