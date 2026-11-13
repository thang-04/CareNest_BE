package com.carenest.service.organization.impl;

import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.RequestMemo;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
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

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<StaffAssignmentSummaryResponse>> findActiveEducationReviewerAssignments(LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findActiveEducationReviewerAssignments";
        log.info("{}|START", logPrefix);
        try {
            Map<Long, List<StaffAssignmentSummaryResponse>> result = staffAssignmentRepository
                    .findActiveByRoles(
                            List.of(StaffRole.PRINCIPAL, StaffRole.VICE_PRINCIPAL, StaffRole.AGE_GROUP_TEAM_LEADER),
                            date)
                    .stream()
                    .collect(Collectors.groupingBy(
                            StaffAssignment::getUserId,
                            Collectors.mapping(staffAssignmentMapper::toSummaryResponse, Collectors.toList())));
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final StaffAssignmentMapper staffAssignmentMapper;
    private final ClassroomRepository classroomRepository;
    private final CampusRepository campusRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final TeacherClassRules teacherClassRules;
    private final Clock clock;

    @Override
    public boolean hasActivePrincipalAssignment(Long userId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasActivePrincipalAssignment|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            boolean active = RequestMemo.get(
                    "principal:" + userId + ":" + date,
                    () -> staffAssignmentRepository.existsActiveByRole(userId, StaffRole.PRINCIPAL, date));
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
            boolean active = RequestMemo.get(
                    "teamLeader:" + userId + ":" + date,
                    () -> staffAssignmentRepository.existsActiveTeamLeader(userId, date));
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
    public boolean hasSubstituteAssignmentOn(Long userId, Long classroomId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasSubstituteAssignmentOn|userId=" + userId + "|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            boolean covered = staffAssignmentRepository.existsSubstituteOn(userId, classroomId, date);
            log.info("{}|END", logPrefix);
            return covered;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean hadRoleInYear(Long userId, StaffRole staffRole, Long campusId, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|hadRoleInYear|userId=" + userId + "|role=" + staffRole + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            boolean had = staffAssignmentRepository.existsRoleInYear(userId, staffRole.name(), campusId, schoolYearId);
            log.info("{}|END", logPrefix);
            return had;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean hasStaffAssignmentInYear(Long userId, Long schoolYearId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|hasStaffAssignmentInYear|userId=" + userId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            boolean has = staffAssignmentRepository.existsStaffAssignmentInYear(userId, schoolYearId, date);
            log.info("{}|END", logPrefix);
            return has;
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
    public Set<Long> findScopedTeamLeaderAgeGroupIds(Long userId, Long schoolYearId, LocalDate today) {
        String logPrefix =
                LOG_PREFIX + "|findScopedTeamLeaderAgeGroupIds|userId=" + userId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ageGroupIds =
                    Set.copyOf(staffAssignmentRepository.findScopedTeamLeaderAgeGroupIds(userId, schoolYearId, today));
            log.info("{}|END", logPrefix);
            return ageGroupIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findYearTeamLeaderIds(Long ageGroupId, Long schoolYearId, LocalDate date) {
        String logPrefix =
                LOG_PREFIX + "|findYearTeamLeaderIds|ageGroupId=" + ageGroupId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> userIds =
                    Set.copyOf(staffAssignmentRepository.findYearTeamLeaderIds(ageGroupId, schoolYearId, date));
            log.info("{}|END", logPrefix);
            return userIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findYearTeamLeaderAgeGroupIds(Long userId, Long schoolYearId, LocalDate date) {
        String logPrefix =
                LOG_PREFIX + "|findYearTeamLeaderAgeGroupIds|userId=" + userId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ageGroupIds =
                    Set.copyOf(staffAssignmentRepository.findYearTeamLeaderAgeGroupIds(userId, schoolYearId, date));
            log.info("{}|END", logPrefix);
            return ageGroupIds;
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
            Set<Long> ageGroupIds = RequestMemo.get(
                    "leaderAgeGroups:" + userId + ":" + date,
                    () -> Set.copyOf(staffAssignmentRepository.findActiveTeamLeaderAgeGroupIds(userId, date)));
            log.info("{}|END", logPrefix);
            return ageGroupIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findActiveTeamLeaderAgeGroupIds(Long userId, Long schoolYearId, LocalDate date) {
        String logPrefix =
                LOG_PREFIX + "|findActiveTeamLeaderAgeGroupIds|userId=" + userId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ageGroupIds = schoolYearId == null
                    ? findActiveTeamLeaderAgeGroupIds(userId, date)
                    : RequestMemo.get(
                            "leaderAgeGroups:" + userId + ":" + schoolYearId + ":" + date,
                            () -> Set.copyOf(staffAssignmentRepository.findActiveTeamLeaderAgeGroupIdsInYear(
                                    userId, schoolYearId, date)));
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
            Set<Long> ageGroupIds = RequestMemo.get(
                    "teacherAgeGroups:" + userId + ":" + date,
                    () -> Set.copyOf(staffAssignmentRepository.findActiveTeacherAgeGroupIds(userId, date)));
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
    public Set<Long> findActiveTeamLeaderIds(Long schoolYearId, Long ageGroupId, LocalDate date) {
        String logPrefix =
                LOG_PREFIX + "|findActiveTeamLeaderIds|schoolYearId=" + schoolYearId + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> userIds =
                    Set.copyOf(staffAssignmentRepository.findActiveTeamLeaderIds(schoolYearId, ageGroupId, date));
            log.info("{}|END", logPrefix);
            return userIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findCampusConfirmerIds(Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findCampusConfirmerIds|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            List<Long> ids = staffAssignmentRepository.findActiveLeaderIds(StaffRole.VICE_PRINCIPAL, campusId, date);
            if (ids.isEmpty()) {
                ids = staffAssignmentRepository.findActiveLeaderIds(StaffRole.PRINCIPAL, null, date);
            }
            Set<Long> result = Set.copyOf(ids);
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findCampusVicePrincipalIds(Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findCampusVicePrincipalIds|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> result =
                    Set.copyOf(staffAssignmentRepository.findActiveLeaderIds(StaffRole.VICE_PRINCIPAL, campusId, date));
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Set<Long> findPrincipalIds(LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findPrincipalIds";
        log.info("{}|START", logPrefix);
        try {
            Set<Long> result =
                    Set.copyOf(staffAssignmentRepository.findActiveLeaderIds(StaffRole.PRINCIPAL, null, date));
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
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
            Set<Long> campusIds = RequestMemo.get(
                    "campuses:" + userId + ":" + staffRole + ":" + date,
                    () -> Set.copyOf(
                            staffRole == StaffRole.TEACHER
                                    ? staffAssignmentRepository.findActiveTeacherCampusIds(userId, date)
                                    : staffAssignmentRepository.findActiveCampusIds(userId, staffRole, date)));
            log.info("{}|END", logPrefix);
            return campusIds;
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
            if (assignment.substitute() && assignment.staffRole() != StaffRole.TEACHER) {
                throw GlobalException.of(ApiCode.SUBSTITUTE_TEACHER_ONLY);
            }
            if (assignment.substitute() && assignment.validTo() == null) {
                throw GlobalException.of(ApiCode.SUBSTITUTE_END_DATE_REQUIRED);
            }
            if (!assignment.substitute() && assignment.replacedTeacherId() != null) {
                throw GlobalException.of(ApiCode.SUBSTITUTE_TEACHER_ONLY);
            }
            LocalDate validTo = assignment.staffRole() == StaffRole.TEACHER
                    ? checkTeacherAssignment(assignment)
                    : assignment.validTo();
            if (staffAssignmentRepository.existsOverlapping(
                    assignment.userId(),
                    assignment.staffRole(),
                    assignment.campusId(),
                    assignment.classroomId(),
                    assignment.validFrom())) {
                throw GlobalException.of(ApiCode.ASSIGNMENT_OVERLAP);
            }
            // AUTH-26: mỗi điểm trường tối đa một PHT (mọi đường tạo phân công PHT áp cùng luật)
            if (assignment.staffRole() == StaffRole.VICE_PRINCIPAL
                    && assignment.campusId() != null
                    && staffAssignmentRepository.existsOtherVicePrincipalAtCampusOpenEnded(
                            assignment.campusId(), assignment.userId(), assignment.validFrom(), validTo)) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
            }
            StaffAssignment saved = staffAssignmentRepository.saveAndFlush(StaffAssignment.create(
                    assignment.userId(),
                    assignment.staffRole(),
                    assignment.campusId(),
                    assignment.classroomId(),
                    assignment.validFrom(),
                    validTo,
                    assignment.assignedBy(),
                    assignment.substitute(),
                    assignment.replacedTeacherId()));
            log.info("{}|assignmentId={}|END", logPrefix, saved.getId());
            return staffAssignmentMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void realignYearAssignments(
            Long schoolYearId, LocalDate oldStart, LocalDate oldEnd, LocalDate newStart, LocalDate newEnd) {
        String logPrefix = LOG_PREFIX + "|realignYearAssignments|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            int ends =
                    oldEnd.equals(newEnd) ? 0 : staffAssignmentRepository.realignYearEnd(schoolYearId, oldEnd, newEnd);
            int starts = oldStart.equals(newStart)
                    ? 0
                    // Phân công đã có hiệu lực (giai đoạn chuẩn bị) giữ nguyên ngày bắt đầu, không ghi đè lịch sử
                    : staffAssignmentRepository.realignYearStart(
                            schoolYearId, oldStart, newStart, LocalDate.now(clock));
            log.info("{}|ends={}|starts={}|END", logPrefix, ends, starts);
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

    /**
     * AUTH-24, cùng luật với PUT /classes/{id}/teachers: GV dạy chính tối đa một lớp/năm; phân công đánh dấu dạy thay
     * được miễn. Phân công lớp không vượt quá năm học của lớp ⇒ trả về ngày kết thúc đã cắt.
     */
    private LocalDate checkTeacherAssignment(NewStaffAssignment assignment) {
        Classroom classroom = classroomRepository
                .findById(assignment.classroomId())
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
        SchoolYear year = schoolYearRepository
                .findById(classroom.getSchoolYearId())
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        Set<Long> teacher = Set.of(assignment.userId());
        teacherClassRules.lockForAssignment(classroom, teacher);
        // Phân công lẻ dạy chính: GV trong điểm trường của lớp; dạy thay được lấy GV cơ sở khác (Cần chốt 11/11/2026)
        teacherClassRules.checkTeachers(classroom, year, teacher, today, assignment.substitute());
        if (!assignment.substitute()) {
            teacherClassRules.checkOneMainClass(classroom, year, teacher, today);
        } else {
            checkReplacedTeacher(assignment);
        }
        LocalDate validTo = assignment.validTo();
        return validTo == null || validTo.isAfter(year.getEndDate()) ? year.getEndDate() : validTo;
    }

    // SCH-04, AUTH-16 (Cần chốt 10/11/2026): dạy thay phải chọn dạy thay cho 1 trong 2 GV đứng lớp; người dạy thay
    // không phải GV đứng lớp đó; mỗi GV đứng lớp chỉ 1 người dạy thay trong cùng khoảng ngày
    private void checkReplacedTeacher(NewStaffAssignment assignment) {
        Long replaced = assignment.replacedTeacherId();
        if (replaced == null) {
            throw GlobalException.of(ApiCode.SUBSTITUTE_REPLACED_REQUIRED);
        }
        if (!staffAssignmentRepository.existsMainTeacherOn(
                assignment.classroomId(), replaced, assignment.validFrom())) {
            throw GlobalException.of(ApiCode.SUBSTITUTE_REPLACED_NOT_IN_CLASS);
        }
        if (staffAssignmentRepository.existsMainTeacherOn(
                assignment.classroomId(), assignment.userId(), assignment.validFrom())) {
            throw GlobalException.of(ApiCode.SUBSTITUTE_IS_CLASS_TEACHER);
        }
        if (staffAssignmentRepository.existsSubstituteFor(
                assignment.classroomId(), replaced, assignment.validFrom(), assignment.validTo())) {
            throw GlobalException.of(ApiCode.SUBSTITUTE_ALREADY_COVERED);
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
