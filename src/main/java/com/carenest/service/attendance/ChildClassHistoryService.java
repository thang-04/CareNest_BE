package com.carenest.service.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ChildClassHistoryService {
    Map<Long, String> statuses(Long classId, Collection<Long> childIds, LocalDate at);

    /**
     * Nhãn lịch sử theo nhiều lớp tại cùng một thời điểm. Dùng cho màn tổng quan để không đọc lại
     * trạng thái xếp lớp cho từng lớp.
     */
    Map<Long, Map<Long, String>> statusesByClass(Map<Long, Set<Long>> childIdsByClass, LocalDate at);
}
