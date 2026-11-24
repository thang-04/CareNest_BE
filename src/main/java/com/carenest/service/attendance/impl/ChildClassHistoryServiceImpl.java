package com.carenest.service.attendance.impl;

import com.carenest.service.attendance.ChildClassHistoryService;
import com.carenest.service.child.EnrollmentService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ChildClassHistoryServiceImpl implements ChildClassHistoryService {
    private static final String LOG_PREFIX = "[ChildClassHistoryServiceImpl]";
    private final EnrollmentService enrollmentService;

    public Map<Long, String> statuses(Long classId, Collection<Long> childIds, LocalDate at) {
        String logPrefix = LOG_PREFIX + "|statuses|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            Map<Long, String> result = new HashMap<>();
            if (!childIds.isEmpty()) {
                var active = enrollmentService.findActiveClassroomIdByChildIds(childIds, at);
                var left = enrollmentService.findChildIdsLeftSchool(childIds, at);
                for (Long childId : childIds) {
                    Long current = active.get(childId);
                    if (current != null && !current.equals(classId)) result.put(childId, "TRANSFERRED");
                    else if (current == null && left.contains(childId)) result.put(childId, "LEFT_SCHOOL");
                }
            }
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
