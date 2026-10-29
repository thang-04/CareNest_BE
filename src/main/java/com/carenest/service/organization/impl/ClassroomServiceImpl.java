package com.carenest.service.organization.impl;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
    private final SchoolYearRepository schoolYearRepository;
    private final AgeGroupRepository ageGroupRepository;

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
    public Optional<ClassroomInfoResponse> findClassroomInfo(Long classroomId) {
        String logPrefix = LOG_PREFIX + "|findClassroomInfo|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            Optional<ClassroomInfoResponse> info = classroomRepository
                    .findById(classroomId)
                    .flatMap(c ->
                            schoolYearRepository.findById(c.getSchoolYearId()).map(y -> toInfo(c, y, ageGroups())));
            log.info("{}|END", logPrefix);
            return info;
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

    @Override
    public List<ClassroomInfoResponse> findActiveYearClassrooms() {
        String logPrefix = LOG_PREFIX + "|findActiveYearClassrooms";
        log.info("{}|START", logPrefix);
        try {
            List<ClassroomInfoResponse> classes = schoolYearRepository
                    .findFirstByStatus(SchoolYearStatus.ACTIVE)
                    .map(year -> {
                        Map<Long, AgeGroup> groups = ageGroups();
                        return classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(year.getId()).stream()
                                .map(c -> toInfo(c, year, groups))
                                .toList();
                    })
                    .orElse(List.of());
            log.info("{}|END", logPrefix);
            return classes;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<ClassroomInfoResponse> findYearClassrooms(Long schoolYearId) {
        if (schoolYearId == null) {
            return findActiveYearClassrooms();
        }
        String logPrefix = LOG_PREFIX + "|findYearClassrooms|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            List<ClassroomInfoResponse> classes = schoolYearRepository
                    .findById(schoolYearId)
                    .map(year -> {
                        Map<Long, AgeGroup> groups = ageGroups();
                        return classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(year.getId()).stream()
                                .map(c -> toInfo(c, year, groups))
                                .toList();
                    })
                    .orElse(List.of());
            log.info("{}|END", logPrefix);
            return classes;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Map<Long, AgeGroup> ageGroups() {
        return ageGroupRepository.findAll().stream().collect(Collectors.toMap(AgeGroup::getId, Function.identity()));
    }

    private static ClassroomInfoResponse toInfo(Classroom c, SchoolYear year, Map<Long, AgeGroup> groups) {
        AgeGroup group = c.getAgeGroupId() == null ? null : groups.get(c.getAgeGroupId());
        List<MealSession> sessions = group == null ? List.of() : List.copyOf(group.getMealSessions());
        return new ClassroomInfoResponse(
                c.getId(),
                c.getName(),
                c.getCampusId(),
                year.getId(),
                year.getStatus(),
                year.getStartDate(),
                year.getEndDate(),
                c.getAgeGroupId(),
                sessions,
                c.getCapacity() == null ? null : c.getCapacity().intValue(),
                c.getStatus());
    }
}
