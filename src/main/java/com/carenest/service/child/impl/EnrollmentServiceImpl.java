package com.carenest.service.child.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.Enrollment;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.service.child.EnrollmentService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ChildRepository childRepository;

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
    public List<ClassChildResponse> findChildrenInClassroom(Long classroomId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findChildrenInClassroom|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> childIds = enrollmentRepository.findActiveByClassroomIds(List.of(classroomId), date).stream()
                    .map(Enrollment::getChildId)
                    .collect(Collectors.toSet());
            List<ClassChildResponse> children = childRepository.findAllById(childIds).stream()
                    .sorted(Comparator.comparing(Child::getFullName).thenComparing(Child::getId))
                    .map(c -> new ClassChildResponse(c.getId(), c.getFullName(), c.getDateOfBirth()))
                    .toList();
            log.info("{}|END", logPrefix);
            return children;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
