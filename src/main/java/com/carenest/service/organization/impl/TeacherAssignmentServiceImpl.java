package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.ClassTeachersResponse;
import com.carenest.dto.organization.TeacherAssignmentResponse;
import com.carenest.dto.organization.TeacherCandidateResponse;
import com.carenest.dto.organization.TeamLeaderRequest;
import com.carenest.dto.organization.TeamLeaderResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.service.organization.TeacherDirectory;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
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
@Transactional
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {
    private static final String LOG_PREFIX = "[TeacherAssignmentServiceImpl]";

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final RoomRepository roomRepository;
    private final TeacherDirectory teacherDirectory;
    private final SchoolConfigAccess schoolConfigAccess;
    private final SchoolStructureMapper schoolStructureMapper;

    @Override
    @Transactional(readOnly = true)
    public TeacherAssignmentResponse getTeacherAssignments(
            CurrentUser currentUser, Long schoolYearId, String campusParam) {
        String logPrefix = LOG_PREFIX + "|getTeacherAssignments|userId=" + currentUser.userId() + "|schoolYearId="
                + schoolYearId + "|campusId=" + campusParam;
        log.info("{}|START", logPrefix);
        try {
            Long campusId = parseCampusId(campusParam);
            Set<Long> viewable = schoolConfigAccess.viewableCampusIds(currentUser);
            if (campusId != null && viewable != null && !viewable.contains(campusId)) {
                throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
            }
            SchoolYear year = findYear(schoolYearId);
            List<Classroom> classes =
                    classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(year.getId()).stream()
                            .filter(c -> campusId != null
                                    ? c.getCampusId().equals(campusId)
                                    : viewable == null || viewable.contains(c.getCampusId()))
                            .toList();
            List<TeacherCandidateResponse> teachers = teacherDirectory.findTeachers(schoolConfigAccess.today()).stream()
                    .filter(t -> campusId != null
                            ? t.campusIds().contains(campusId)
                            : viewable == null || t.campusIds().stream().anyMatch(viewable::contains))
                    .toList();
            TeacherAssignmentResponse response = new TeacherAssignmentResponse(
                    schoolStructureMapper.toDetailResponse(year, classroomRepository.countBySchoolYearId(year.getId())),
                    ageGroups(),
                    toClassTeachers(classes),
                    teachers);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ClassTeachersResponse assignClassTeachers(
            CurrentUser currentUser, Long classId, ClassTeachersRequest request) {
        String logPrefix = LOG_PREFIX + "|assignClassTeachers|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            Classroom classroom = classroomRepository
                    .findById(classId)
                    .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Class not found"));
            SchoolYear year = findYear(classroom.getSchoolYearId());
            checkOpenYear(year);
            Set<Long> teacherIds = new HashSet<>(request.teacherIds());
            if (teacherIds.size() != 2) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Choose 2 different teachers for the class");
            }
            if (!teacherIds.contains(request.homeroomTeacherId())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Homeroom teacher must be one of the class teachers");
            }
            // Chỉ phân công giáo viên thuộc điểm trường của lớp
            Set<Long> campusTeachers = teacherDirectory.findTeachers(schoolConfigAccess.today()).stream()
                    .filter(t -> t.campusIds().contains(classroom.getCampusId()))
                    .map(TeacherCandidateResponse::id)
                    .collect(Collectors.toSet());
            if (!campusTeachers.containsAll(teacherIds)) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Only teachers of the class campus can be assigned");
            }
            LocalDate start = effectiveStart(year);
            List<StaffAssignment> current =
                    staffAssignmentRepository.findCurrentTeachers(List.of(classId), schoolConfigAccess.today());
            Set<Long> kept = new HashSet<>();
            for (StaffAssignment assignment : current) {
                if (teacherIds.contains(assignment.getUserId()) && kept.add(assignment.getUserId())) {
                    assignment.markHomeroom(assignment.getUserId().equals(request.homeroomTeacherId()));
                } else {
                    endOrRemove(assignment, start);
                }
            }
            for (Long teacherId : teacherIds) {
                if (!kept.contains(teacherId)) {
                    staffAssignmentRepository.save(StaffAssignment.teacher(
                            teacherId,
                            classId,
                            teacherId.equals(request.homeroomTeacherId()),
                            start,
                            year.getEndDate(),
                            currentUser.userId()));
                }
            }
            staffAssignmentRepository.flush();
            // TODO(Cần): thông báo giáo viên mới được phân công — chờ module notification
            ClassTeachersResponse response = toClassTeachers(List.of(classroom)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TeamLeaderResponse getTeamLeaders(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getTeamLeaders|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.viewableCampusIds(currentUser);
            TeamLeaderResponse response = toTeamLeaderResponse(findYear(schoolYearId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public TeamLeaderResponse assignTeamLeader(CurrentUser currentUser, TeamLeaderRequest request) {
        String logPrefix = LOG_PREFIX + "|assignTeamLeader|userId=" + currentUser.userId() + "|schoolYearId="
                + request.schoolYearId() + "|ageGroupId=" + request.ageGroupId();
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYear year = findYear(request.schoolYearId());
            checkOpenYear(year);
            if (!ageGroupRepository.existsById(request.ageGroupId())) {
                throw new GlobalException(ApiCode.NOT_FOUND, "Age group not found");
            }
            if (request.userId() != null
                    && teacherDirectory.findTeachers(schoolConfigAccess.today()).stream()
                            .noneMatch(t -> t.id().equals(request.userId()))) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Team leader must be a working teacher");
            }
            LocalDate start = effectiveStart(year);
            // 1 tổ trưởng/nhóm tuổi/năm cho cả trường; 1 giáo viên làm tổ trưởng tối đa 1 nhóm tuổi trong năm
            for (StaffAssignment leader : currentLeaders(year)) {
                boolean sameGroup = leader.getAgeGroupId().equals(request.ageGroupId());
                boolean sameUser = leader.getUserId().equals(request.userId());
                if (sameGroup && sameUser) {
                    staffAssignmentRepository.flush();
                    log.info("{}|unchanged|END", logPrefix);
                    return toTeamLeaderResponse(year);
                }
                if (sameGroup || sameUser) {
                    endOrRemove(leader, start);
                }
            }
            if (request.userId() != null) {
                staffAssignmentRepository.save(StaffAssignment.teamLeader(
                        request.userId(),
                        request.ageGroupId(),
                        year.getId(),
                        start,
                        year.getEndDate(),
                        currentUser.userId()));
            }
            staffAssignmentRepository.flush();
            TeamLeaderResponse response = toTeamLeaderResponse(year);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Phân công đã có hiệu lực ⇒ kết thúc trước ngày bắt đầu mới (giữ lịch sử quyền); chưa có hiệu lực ⇒ xóa
    private void endOrRemove(StaffAssignment assignment, LocalDate start) {
        if (assignment.getValidFrom().isBefore(start)) {
            assignment.endBefore(start);
        } else {
            staffAssignmentRepository.delete(assignment);
        }
    }

    // Năm đang chạy: áp dụng từ hôm nay; năm sắp tới: từ ngày bắt đầu năm học
    private LocalDate effectiveStart(SchoolYear year) {
        LocalDate today = schoolConfigAccess.today();
        return today.isAfter(year.getStartDate()) ? today : year.getStartDate();
    }

    private List<StaffAssignment> currentLeaders(SchoolYear year) {
        return staffAssignmentRepository.findCurrentTeamLeaders(year.getId(), year.getEndDate(), effectiveStart(year));
    }

    private TeamLeaderResponse toTeamLeaderResponse(SchoolYear year) {
        List<StaffAssignment> leaders = currentLeaders(year);
        List<TeacherCandidateResponse> teachers = teacherDirectory.findTeachers(schoolConfigAccess.today());
        Set<Long> userIds = leaders.stream().map(StaffAssignment::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(userIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        Map<Long, Long> leadsByUser = leaders.stream()
                .collect(Collectors.toMap(StaffAssignment::getUserId, StaffAssignment::getAgeGroupId, (a, b) -> a));
        Map<Long, Long> classCounts =
                classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(year.getId()).stream()
                        .filter(c -> c.getAgeGroupId() != null)
                        .collect(Collectors.groupingBy(Classroom::getAgeGroupId, Collectors.counting()));
        List<AgeGroupDetailResponse> groups = ageGroups();
        Map<Long, String> groupNames = groups.stream()
                .collect(Collectors.toMap(AgeGroupDetailResponse::id, AgeGroupDetailResponse::shortName));
        Map<Long, Long> teacherCampus = teachers.stream()
                .filter(t -> t.campusId() != null)
                .collect(Collectors.toMap(TeacherCandidateResponse::id, TeacherCandidateResponse::campusId));
        return new TeamLeaderResponse(
                year.getId(),
                leaders.stream()
                        .map(l -> new TeamLeaderResponse.Leader(
                                l.getAgeGroupId(),
                                l.getUserId(),
                                names.get(l.getUserId()),
                                teacherCampus.get(l.getUserId())))
                        .toList(),
                groups.stream()
                        .map(g -> new TeamLeaderResponse.LeaderGroup(
                                g.id(),
                                g.code(),
                                g.name(),
                                g.shortName(),
                                g.ageFromMonths(),
                                g.ageToMonths(),
                                g.mealSessions(),
                                g.maxClassSize(),
                                classCounts.getOrDefault(g.id(), 0L)))
                        .toList(),
                teachers.stream()
                        .map(t -> {
                            Long leads = leadsByUser.get(t.id());
                            return new TeamLeaderResponse.Candidate(
                                    t.id(),
                                    t.fullName(),
                                    t.campusId(),
                                    t.campusIds(),
                                    leads,
                                    leads == null ? null : groupNames.get(leads));
                        })
                        .toList());
    }

    private List<ClassTeachersResponse> toClassTeachers(List<Classroom> classes) {
        if (classes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = classes.stream().map(Classroom::getId).toList();
        Map<Long, List<StaffAssignment>> teachers =
                staffAssignmentRepository.findCurrentTeachers(ids, schoolConfigAccess.today()).stream()
                        .collect(Collectors.groupingBy(StaffAssignment::getClassroomId));
        Map<Long, Long> children = classroomRepository.countActiveChildren(ids, schoolConfigAccess.today()).stream()
                .collect(Collectors.toMap(
                        ClassroomRepository.ClassroomCount::getClassroomId,
                        ClassroomRepository.ClassroomCount::getTotal));
        return classes.stream()
                .map(c -> {
                    List<StaffAssignment> rows = teachers.getOrDefault(c.getId(), List.of());
                    return new ClassTeachersResponse(
                            c.getId(),
                            c.getName(),
                            c.getCampusId(),
                            c.getAgeGroupId(),
                            children.getOrDefault(c.getId(), 0L),
                            rows.stream()
                                    .map(StaffAssignment::getUserId)
                                    .distinct()
                                    .toList(),
                            rows.stream()
                                    .filter(StaffAssignment::isHomeroom)
                                    .map(StaffAssignment::getUserId)
                                    .findFirst()
                                    .orElse(null));
                })
                .toList();
    }

    private List<AgeGroupDetailResponse> ageGroups() {
        return ageGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(
                                (AgeGroup g) -> g.getAgeFromMonths() == null ? Short.MAX_VALUE : g.getAgeFromMonths())
                        .thenComparing(AgeGroup::getSortOrder))
                .map(schoolStructureMapper::toDetailResponse)
                .toList();
    }

    // FE gửi campusId = "all" khi xem toàn trường
    private static Long parseCampusId(String campusParam) {
        if (campusParam == null || campusParam.isBlank() || "all".equalsIgnoreCase(campusParam)) {
            return null;
        }
        if (!campusParam.matches("\\d+")) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "campusId must be a number or 'all'");
        }
        return Long.valueOf(campusParam);
    }

    private static void checkOpenYear(SchoolYear year) {
        if (year.getStatus() == SchoolYearStatus.CLOSED) {
            throw new GlobalException(ApiCode.CONFLICT, "School year " + year.getName() + " is closed");
        }
    }

    private SchoolYear findYear(Long schoolYearId) {
        if (schoolYearId == null) {
            return schoolYearRepository
                    .findFirstByStatus(SchoolYearStatus.ACTIVE)
                    .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "No active school year"));
        }
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "School year not found"));
    }
}
