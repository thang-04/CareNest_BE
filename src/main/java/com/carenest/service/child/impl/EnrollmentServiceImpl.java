package com.carenest.service.child.impl;

import com.carenest.entity.child.Enrollment;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.service.child.EnrollmentService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {
    private static final String LOG_PREFIX = "[EnrollmentServiceImpl]";

    private final EnrollmentRepository enrollmentRepository;

    @Override
    public List<Long> findActiveClassroomIds(Long childId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveClassroomIds|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            List<Long> classroomIds = enrollmentRepository.findActiveClassroomIds(childId, date);
            log.info("{}|END", logPrefix);
            return classroomIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, Long> findActiveClassroomIdByChildIds(Collection<Long> childIds, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveClassroomIdByChildIds|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, Long> classroomByChild = new HashMap<>();
            if (!childIds.isEmpty()) {
                // Query sắp mới nhất trước: giữ bản ghi đầu tiên của mỗi trẻ
                for (Enrollment enrollment : enrollmentRepository.findActiveByChildIdIn(childIds, date)) {
                    classroomByChild.putIfAbsent(enrollment.getChildId(), enrollment.getClassroomId());
                }
            }
            log.info("{}|END", logPrefix);
            return classroomByChild;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
