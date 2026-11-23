package com.carenest.service.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface ChildClassHistoryService {
    Map<Long, String> statuses(Long classId, Collection<Long> childIds, LocalDate at);
}
