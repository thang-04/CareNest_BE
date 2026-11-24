package com.carenest.service.attendance.impl;

import com.carenest.service.attendance.ChildClassHistoryService;
import com.carenest.service.child.EnrollmentService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    @Override
    public Map<Long, Map<Long, String>> statusesByClass(Map<Long, Set<Long>> childIdsByClass, LocalDate at) {
        String logPrefix = LOG_PREFIX + "|statusesByClass|classes=" + childIdsByClass.size();
        log.info("{}|START", logPrefix);
        try {
            Set<Long> childIds = new HashSet<>();
            childIdsByClass.values().forEach(childIds::addAll);
            if (childIds.isEmpty()) return Map.of();

            Map<Long, Long> active = enrollmentService.findActiveClassroomIdByChildIds(childIds, at);
            Set<Long> left = enrollmentService.findChildIdsLeftSchool(childIds, at);
            Map<Long, Map<Long, String>> result = new HashMap<>();
            childIdsByClass.forEach((classId, classChildIds) -> {
                Map<Long, String> statuses = new HashMap<>();
                for (Long childId : classChildIds) {
                    Long current = active.get(childId);
                    if (current != null && !current.equals(classId)) statuses.put(childId, "TRANSFERRED");
                    else if (current == null && left.contains(childId)) statuses.put(childId, "LEFT_SCHOOL");
                }
                result.put(classId, statuses);
            });
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
