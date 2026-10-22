package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AssignmentHistoryResponse;
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
                    toClassTeachers(classes, asOf(year)),
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
            SchoolYearRules.checkOpen(year);
            Set<Long> teacherIds = new HashSet<>(request.teacherIds());
            LocalDate start = effectiveStart(year);
            List<StaffAssignment> current =
                    staffAssignmentRepository.findCurrentTeachers(List.of(classId), schoolConfigAccess.today());
            // Danh sách rỗng: gỡ toàn bộ giáo viên (phân công đã chạy kết thúc hôm trước, lịch sử giữ lại).
            // Năm đang học thì lớp phải còn giáo viên: chỉ gỡ kèm người thay thế (Cần chốt 22/10/2026)
            if (teacherIds.isEmpty() && year.getStatus() == SchoolYearStatus.ACTIVE) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "A class of the active school year needs a replacement teacher");
            }
            if (teacherIds.isEmpty()) {
                current.forEach(assignment -> endOrRemove(assignment, start));
                staffAssignmentRepository.flush();
                ClassTeachersResponse response = toClassTeachers(List.of(classroom), schoolConfigAccess.today())
                        .getFirst();
                log.info("{}|removedAll|END", logPrefix);
                return response;
            }
            if (teacherIds.size() != request.teacherIds().size()) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Choose 2 different teachers for the class");
            }
            checkAccountsActive(teacherIds);
            // Lớp còn 1 giáo viên mà không chọn chủ nhiệm (vd. vừa gỡ chủ nhiệm) ⇒ người còn lại làm chủ nhiệm
            Long homeroomId = request.homeroomTeacherId() == null && teacherIds.size() == 1
                    ? teacherIds.iterator().next()
                    : request.homeroomTeacherId();
            // Chủ nhiệm không bắt buộc khi lưu (chọn sau được); có chọn thì phải là giáo viên của lớp
            if (homeroomId != null && !teacherIds.contains(homeroomId)) {
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
            checkOneClassPerTeacher(classroom, teacherIds);
            Set<Long> kept = new HashSet<>();
            for (StaffAssignment assignment : current) {
                if (teacherIds.contains(assignment.getUserId()) && kept.add(assignment.getUserId())) {
                    assignment.markHomeroom(assignment.getUserId().equals(homeroomId));
                } else {
                    endOrRemove(assignment, start);
                }
            }
            for (Long teacherId : teacherIds) {
                if (!kept.contains(teacherId)) {
                    staffAssignmentRepository.save(StaffAssignment.teacher(
                            teacherId,
                            classId,
                            teacherId.equals(homeroomId),
                            start,
                            year.getEndDate(),
                            currentUser.userId()));
                }
            }
            staffAssignmentRepository.flush();
            // TODO(Cần): thông báo giáo viên mới được phân công — chờ module notification
            ClassTeachersResponse response = toClassTeachers(List.of(classroom), schoolConfigAccess.today())
                    .getFirst();
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
            SchoolYearRules.checkOpen(year);
            if (!ageGroupRepository.existsById(request.ageGroupId())) {
                throw new GlobalException(ApiCode.NOT_FOUND, "Age group not found");
            }
            if (request.userId() != null) {
                checkAccountsActive(Set.of(request.userId()));
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
    @Override
    public List<AssignmentHistoryResponse> getClassTeacherHistory(CurrentUser currentUser, Long classId) {
        String logPrefix =
                LOG_PREFIX + "|getClassTeacherHistory|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            Classroom classroom = classroomRepository
                    .findById(classId)
                    .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Class not found"));
            schoolConfigAccess.checkCanViewCampus(currentUser, classroom.getCampusId());
            List<AssignmentHistoryResponse> history = toHistory(staffAssignmentRepository.findTeacherHistory(classId));
            log.info("{}|END", logPrefix);
            return history;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<AssignmentHistoryResponse> getTeamLeaderHistory(
            CurrentUser currentUser, Long ageGroupId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getTeamLeaderHistory|userId=" + currentUser.userId() + "|ageGroupId="
                + ageGroupId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            if (!ageGroupRepository.existsById(ageGroupId)) {
                throw new GlobalException(ApiCode.NOT_FOUND, "Age group not found");
            }
            SchoolYear year = findYear(schoolYearId);
            List<AssignmentHistoryResponse> history = toHistory(staffAssignmentRepository.findTeamLeaderHistory(
                    ageGroupId, year.getId(), year.getStartDate(), year.getEndDate()));
            log.info("{}|END", logPrefix);
            return history;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<AssignmentHistoryResponse> toHistory(List<StaffAssignment> rows) {
        Set<Long> userIds = new HashSet<>();
        rows.forEach(a -> {
            userIds.add(a.getUserId());
            if (a.getAssignedBy() != null) {
                userIds.add(a.getAssignedBy());
            }
        });
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(userIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        LocalDate today = schoolConfigAccess.today();
        return rows.stream()
                .map(a -> new AssignmentHistoryResponse(
                        a.getId(),
                        a.getUserId(),
                        names.get(a.getUserId()),
                        a.isHomeroom(),
                        a.getValidFrom(),
                        a.getValidTo(),
                        a.getValidTo() == null || !a.getValidTo().isBefore(today),
                        a.getAssignedBy(),
                        a.getAssignedBy() == null ? null : names.get(a.getAssignedBy()),
                        a.getCreatedAt()))
                .toList();
    }

    private void checkAccountsActive(Set<Long> userIds) {
        if (!staffAssignmentRepository.findInactiveUserIds(userIds).isEmpty()) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "Teacher account is locked");
        }
    }

    // Mỗi giáo viên dạy đúng một lớp trong năm học (Cần chốt 18/10/2026)
    private void checkOneClassPerTeacher(Classroom classroom, Set<Long> teacherIds) {
        Map<Long, String> otherClasses =
                classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(classroom.getSchoolYearId()).stream()
                        .filter(c -> !c.getId().equals(classroom.getId()))
                        .collect(Collectors.toMap(Classroom::getId, Classroom::getName));
        if (otherClasses.isEmpty()) {
            return;
        }
        staffAssignmentRepository.findCurrentTeachers(otherClasses.keySet(), schoolConfigAccess.today()).stream()
                .filter(a -> teacherIds.contains(a.getUserId()))
                .findFirst()
                .ifPresent(a -> {
                    String name = roomRepository.findUserNames(Set.of(a.getUserId())).stream()
                            .map(RoomRepository.UserName::getFullName)
                            .findFirst()
                            .orElse("#" + a.getUserId());
                    throw new GlobalException(
                            ApiCode.CONFLICT,
                            "Teacher " + name + " already teaches class " + otherClasses.get(a.getClassroomId()));
                });
    }

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

    // Năm đã kết thúc: xem giáo viên / sĩ số như lúc kết thúc năm (không theo hôm nay)
    private LocalDate asOf(SchoolYear year) {
        LocalDate today = schoolConfigAccess.today();
        if (year.getStatus() != SchoolYearStatus.CLOSED) {
            return today;
        }
        LocalDate end = year.getEndDate();
        if (year.getClosedAt() != null) {
            LocalDate closedDay = LocalDate.ofInstant(year.getClosedAt(), schoolConfigAccess.zone());
            end = closedDay.isBefore(end) ? closedDay : end;
        }
        return end.isBefore(today) ? end : today;
    }

    private List<ClassTeachersResponse> toClassTeachers(List<Classroom> classes, LocalDate asOf) {
        if (classes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = classes.stream().map(Classroom::getId).toList();
        Map<Long, List<StaffAssignment>> teachers = staffAssignmentRepository.findCurrentTeachers(ids, asOf).stream()
                .collect(Collectors.groupingBy(StaffAssignment::getClassroomId));
        Set<Long> teacherUserIds = teachers.values().stream()
                .flatMap(List::stream)
                .map(StaffAssignment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> names = teacherUserIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(teacherUserIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        Map<Long, Long> children = classroomRepository.countActiveChildren(ids, asOf).stream()
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
                            c.getCapacity() == null ? null : c.getCapacity().intValue(),
                            rows.stream()
                                    .map(StaffAssignment::getUserId)
                                    .distinct()
                                    .toList(),
                            rows.stream()
                                    .filter(StaffAssignment::isHomeroom)
                                    .map(StaffAssignment::getUserId)
                                    .findFirst()
                                    .orElse(null),
                            rows.stream()
                                    .map(StaffAssignment::getUserId)
                                    .distinct()
                                    .map(id -> new ClassTeachersResponse.Teacher(id, names.get(id)))
                                    .toList());
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
