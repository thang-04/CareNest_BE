package com.carenest.service.organization.impl;

import com.carenest.entity.organization.Classroom;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.service.organization.ClassroomService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomServiceImpl implements ClassroomService {
    private static final String LOG_PREFIX = "[ClassroomServiceImpl]";

    private final ClassroomRepository classroomRepository;

    @Override
    public Optional<Long> findCampusId(Long classroomId) {
        String logPrefix = LOG_PREFIX + "|findCampusId|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            Optional<Long> campusId = classroomRepository.findCampusIdById(classroomId);
            log.info("{}|END", logPrefix);
            return campusId;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, Long> findCampusIds(Collection<Long> classroomIds) {
        String logPrefix = LOG_PREFIX + "|findCampusIds|classrooms=" + classroomIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, Long> campusIds = classroomIds.isEmpty()
                    ? Map.of()
                    : classroomRepository.findAllById(classroomIds).stream()
                            .collect(Collectors.toMap(Classroom::getId, Classroom::getCampusId));
            log.info("{}|END", logPrefix);
            return campusIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<Long> findClassroomIdsOfCampuses(Collection<Long> campusIds) {
        String logPrefix = LOG_PREFIX + "|findClassroomIdsOfCampuses|campuses=" + campusIds.size();
        log.info("{}|START", logPrefix);
        try {
            List<Long> ids = campusIds.isEmpty() ? List.of() : classroomRepository.findIdsByCampusIdIn(campusIds);
            log.info("{}|END", logPrefix);
            return ids;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
