package com.carenest.service.organization.impl;

import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ClassroomRepository classroomRepository;
    private final CampusRepository campusRepository;

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
    public boolean hasActiveTeamLeaderAssignment(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasActiveTeamLeaderAssignment|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            boolean active = staffAssignmentRepository.existsActiveTeamLeader(userId, date);
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
    public Map<Long, Long> findHomeroomTeacherIds(Collection<Long> classroomIds, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findHomeroomTeacherIds|classCount=" + classroomIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, Long> homeroom = classroomIds.isEmpty()
                    ? Map.of()
                    : staffAssignmentRepository.findCurrentTeachers(classroomIds, date).stream()
                            .filter(StaffAssignment::isHomeroom)
                            .collect(Collectors.toMap(
                                    StaffAssignment::getClassroomId,
                                    StaffAssignment::getUserId,
                                    (first, second) -> first));
            log.info("{}|END", logPrefix);
            return homeroom;
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

    @Override
    public Set<Long> findActiveCampusIds(Long userId, StaffRole staffRole, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveCampusIds|userId=" + userId + "|role=" + staffRole;
        log.info("{}|START", logPrefix);
        try {
            List<Long> campusIds = staffRole == StaffRole.TEACHER
                    ? staffAssignmentRepository.findActiveTeacherCampusIds(userId, date)
                    : staffAssignmentRepository.findActiveCampusIds(userId, staffRole, date);
            log.info("{}|END", logPrefix);
            return Set.copyOf(campusIds);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findActiveStaffUserIds(Collection<Long> campusIds, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveStaffUserIds|campuses=" + campusIds.size();
        log.info("{}|START", logPrefix);
        try {
            Set<Long> userIds = new HashSet<>();
            if (!campusIds.isEmpty()) {
                userIds.addAll(staffAssignmentRepository.findActiveTeacherUserIds(campusIds, date));
                userIds.addAll(staffAssignmentRepository.findActiveCampusStaffUserIds(campusIds, date));
            }
            log.info("{}|END", logPrefix);
            return userIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, List<StaffAssignmentSummaryResponse>> findActiveAssignmentsByUserIds(
            Collection<Long> userIds, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveAssignmentsByUserIds|users=" + userIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, List<StaffAssignmentSummaryResponse>> byUser = userIds.isEmpty()
                    ? Map.of()
                    : staffAssignmentRepository.findActiveByUserIdIn(userIds, date).stream()
                            .collect(Collectors.groupingBy(
                                    StaffAssignment::getUserId,
                                    Collectors.mapping(staffAssignmentMapper::toSummaryResponse, Collectors.toList())));
            log.info("{}|END", logPrefix);
            return byUser;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<StaffAssignmentResponse> findAssignments(Long userId) {
        String logPrefix = LOG_PREFIX + "|findAssignments|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            List<StaffAssignmentResponse> assignments =
                    staffAssignmentRepository.findByUserIdOrderByValidFromDescIdDesc(userId).stream()
                            .map(staffAssignmentMapper::toResponse)
                            .toList();
            log.info("{}|END", logPrefix);
            return assignments;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Optional<StaffAssignmentResponse> findAssignment(Long assignmentId) {
        String logPrefix = LOG_PREFIX + "|findAssignment|assignmentId=" + assignmentId;
        log.info("{}|START", logPrefix);
        try {
            Optional<StaffAssignmentResponse> assignment =
                    staffAssignmentRepository.findById(assignmentId).map(staffAssignmentMapper::toResponse);
            log.info("{}|END", logPrefix);
            return assignment;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Long resolveCampusId(StaffRole staffRole, Long campusId, Long classroomId) {
        String logPrefix = LOG_PREFIX + "|resolveCampusId|role=" + staffRole;
        log.info("{}|START", logPrefix);
        try {
            Long resolved = resolveScope(staffRole, campusId, classroomId);
            log.info("{}|END", logPrefix);
            return resolved;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public StaffAssignmentResponse createAssignment(NewStaffAssignment assignment) {
        String logPrefix =
                LOG_PREFIX + "|createAssignment|userId=" + assignment.userId() + "|role=" + assignment.staffRole();
        log.info("{}|START", logPrefix);
        try {
            resolveScope(assignment.staffRole(), assignment.campusId(), assignment.classroomId());
            if (assignment.validFrom() == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "ngày bắt đầu phân công");
            }
            if (assignment.validTo() != null && assignment.validTo().isBefore(assignment.validFrom())) {
                throw GlobalException.of(ApiCode.ASSIGNMENT_END_BEFORE_START);
            }
            if (staffAssignmentRepository.existsOverlapping(
                    assignment.userId(),
                    assignment.staffRole(),
                    assignment.campusId(),
                    assignment.classroomId(),
                    assignment.validFrom())) {
                throw GlobalException.of(ApiCode.ASSIGNMENT_OVERLAP);
            }
            StaffAssignment saved = staffAssignmentRepository.saveAndFlush(StaffAssignment.create(
                    assignment.userId(),
                    assignment.staffRole(),
                    assignment.campusId(),
                    assignment.classroomId(),
                    assignment.validFrom(),
                    assignment.validTo()));
            log.info("{}|assignmentId={}|END", logPrefix, saved.getId());
            return staffAssignmentMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public StaffAssignmentResponse endAssignment(Long assignmentId, LocalDate lastDay) {
        String logPrefix = LOG_PREFIX + "|endAssignment|assignmentId=" + assignmentId;
        log.info("{}|START", logPrefix);
        try {
            StaffAssignment assignment = staffAssignmentRepository
                    .findById(assignmentId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.STAFF_ASSIGNMENT_NOT_FOUND));
            if (lastDay.isBefore(assignment.getValidFrom())) {
                throw GlobalException.of(ApiCode.ASSIGNMENT_END_BEFORE_START);
            }
            // Đã kết thúc sớm hơn thì không kéo dài ngược: muốn tiếp tục ⇒ tạo phân công mới
            if (assignment.getValidTo() != null && assignment.getValidTo().isBefore(lastDay)) {
                throw GlobalException.of(ApiCode.ASSIGNMENT_ALREADY_ENDED);
            }
            assignment.endOn(lastDay);
            StaffAssignment saved = staffAssignmentRepository.saveAndFlush(assignment);
            log.info("{}|END", logPrefix);
            return staffAssignmentMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Hình dạng phạm vi khớp CHECK ck_staff_assignments_scope (V2); campus/lớp phải tồn tại
    private Long resolveScope(StaffRole staffRole, Long campusId, Long classroomId) {
        return switch (staffRole) {
            case PRINCIPAL -> {
                if (campusId != null || classroomId != null) {
                    throw GlobalException.of(ApiCode.PRINCIPAL_ASSIGNMENT_SCHOOL_WIDE);
                }
                yield null;
            }
            case VICE_PRINCIPAL, KITCHEN_STAFF -> {
                if (campusId == null || classroomId != null) {
                    throw GlobalException.of(ApiCode.ASSIGNMENT_CAMPUS_REQUIRED);
                }
                if (!campusRepository.existsById(campusId)) {
                    throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
                }
                yield campusId;
            }
            case TEACHER -> {
                if (classroomId == null || campusId != null) {
                    throw GlobalException.of(ApiCode.ASSIGNMENT_CLASS_REQUIRED);
                }
                yield classroomRepository
                        .findCampusIdById(classroomId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
            }
            // Tổ trưởng gắn nhóm tuổi + năm học, phân công qua TeacherAssignmentService (luật 1 tổ trưởng/nhóm/năm)
            case AGE_GROUP_TEAM_LEADER -> throw GlobalException.of(ApiCode.TEAM_LEADER_ASSIGNMENT_NOT_HERE);
        };
    }
}
