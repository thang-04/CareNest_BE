package com.carenest.service.organization.impl;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
public class StaffAssignmentServiceImpl implements StaffAssignmentService {
    private static final String LOG_PREFIX = "[StaffAssignmentServiceImpl]";

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final StaffAssignmentMapper staffAssignmentMapper;

    @Override
    public boolean hasActivePrincipalAssignment(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasActivePrincipalAssignment|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            boolean active = staffAssignmentRepository.existsActiveByRole(userId, StaffRole.PRINCIPAL, date);
            log.info("{}|END", logPrefix);
            return active;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasActiveCampusAssignment|userId=" + userId + "|role=" + staffRole
                + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            boolean active = staffAssignmentRepository.existsActiveForCampus(userId, staffRole, campusId, date);
            log.info("{}|END", logPrefix);
            return active;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasActiveTeacherAssignment|userId=" + userId + "|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            boolean active = staffAssignmentRepository.existsActiveTeacherForClassroom(userId, classroomId, date);
            log.info("{}|END", logPrefix);
            return active;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean hasTeacherAssignmentHistory(Long userId, Long classroomId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasTeacherAssignmentHistory|userId=" + userId + "|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            boolean taught = staffAssignmentRepository.existsPastTeacherForClassroom(userId, classroomId, date);
            log.info("{}|END", logPrefix);
            return taught;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveAssignments|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            List<StaffAssignmentSummaryResponse> assignments =
                    staffAssignmentRepository.findActiveByUserId(userId, date).stream()
                            .map(staffAssignmentMapper::toSummaryResponse)
                            .toList();
            log.info("{}|END", logPrefix);
            return assignments;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findActiveTeamLeaderAgeGroupIds(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveTeamLeaderAgeGroupIds|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ageGroupIds = Set.copyOf(staffAssignmentRepository.findActiveTeamLeaderAgeGroupIds(userId, date));
            log.info("{}|END", logPrefix);
            return ageGroupIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findActiveTeacherAgeGroupIds(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveTeacherAgeGroupIds|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ageGroupIds = Set.copyOf(staffAssignmentRepository.findActiveTeacherAgeGroupIds(userId, date));
            log.info("{}|END", logPrefix);
            return ageGroupIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findCurrentTeacherClassroomIds(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findCurrentTeacherClassroomIds|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> classroomIds = Set.copyOf(staffAssignmentRepository.findCurrentTeacherClassroomIds(userId, date));
            log.info("{}|END", logPrefix);
            return classroomIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, List<Long>> findCurrentTeacherIds(Collection<Long> classroomIds, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findCurrentTeacherIds|classCount=" + classroomIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, List<Long>> teachers = classroomIds.isEmpty()
                    ? Map.of()
                    : staffAssignmentRepository.findCurrentTeachers(classroomIds, date).stream()
                            .collect(Collectors.groupingBy(
                                    StaffAssignment::getClassroomId,
                                    Collectors.mapping(StaffAssignment::getUserId, Collectors.toList())));
            log.info("{}|END", logPrefix);
            return teachers;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findActiveTeamLeaderIds(Long ageGroupId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveTeamLeaderIds|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> userIds = Set.copyOf(staffAssignmentRepository.findActiveTeamLeaderIds(ageGroupId, date));
            log.info("{}|END", logPrefix);
            return userIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
