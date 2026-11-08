package com.carenest.service.organization.impl;

import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.CopyClassesRequest;
import com.carenest.dto.organization.CopyClassesResponse;
import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.SchoolYearSemesterRequest;
import com.carenest.dto.organization.TeacherCandidateResponse;
import com.carenest.dto.organization.TeamLeaderRequest;
import com.carenest.dto.organization.YearCopyRequest;
import com.carenest.dto.organization.YearCopyResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.AttendanceCutoff;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.service.organization.TeacherDirectory;
import com.carenest.service.organization.YearCopyService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.YearCopyPart;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Chép cấu hình năm học nguồn sang năm sắp tới (Cần chốt 30/10/2026: màn Thiết lập năm học 7 bước, chọn năm cũ thì
 * điền sẵn mọi bước). Không mở transaction chung: mỗi lớp / nhóm tuổi / phần gọi service riêng với đủ kiểm tra như
 * thao tác tay, lỗi ở một dòng chỉ làm dòng đó bị bỏ qua (kèm lý do) còn các dòng khác vẫn được chép.
 * Dữ liệu năm đích đã có (lớp trùng tên, lớp đã có GV, nhóm tuổi đã có tổ trưởng, giờ chốt, học kỳ) giữ nguyên.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YearCopyServiceImpl implements YearCopyService {
    private static final String LOG_PREFIX = "[YearCopyServiceImpl]";

    private final SchoolYearRepository schoolYearRepository;
    private final ClassroomRepository classroomRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final AttendanceCutoffRepository attendanceCutoffRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final RoomRepository roomRepository;
    private final TeacherDirectory teacherDirectory;
    private final SchoolConfigAccess schoolConfigAccess;
    private final ClassManagementService classManagementService;
    private final TeacherAssignmentService teacherAssignmentService;
    private final AttendanceCutoffService attendanceCutoffService;
    private final SchoolYearService schoolYearService;

    @Override
    public YearCopyResponse copyYear(CurrentUser currentUser, Long targetYearId, YearCopyRequest request) {
        String logPrefix = LOG_PREFIX + "|copyYear|userId=" + currentUser.userId() + "|targetYearId=" + targetYearId
                + "|sourceYearId=" + request.sourceYearId();
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            if (request.parts() == null || request.parts().isEmpty()) {
                throw GlobalException.of(ApiCode.YEAR_COPY_PARTS_REQUIRED);
            }
            SchoolYear target = findYear(targetYearId);
            SchoolYear source = findYear(request.sourceYearId());
            if (source.getId().equals(target.getId())) {
                throw GlobalException.of(ApiCode.COPY_SOURCE_YEAR_INVALID);
            }
            if (target.getStatus() != SchoolYearStatus.PLANNED) {
                throw GlobalException.of(
                        ApiCode.YEAR_COPY_TARGET_NOT_PLANNED,
                        target.getName(),
                        target.getStatus() == SchoolYearStatus.ACTIVE ? "hoạt động" : "đã kết thúc");
            }
            Set<YearCopyPart> parts = request.parts();
            List<YearCopyResponse.Skipped> skipped = new ArrayList<>();
            List<String> copiedClasses = List.of();
            List<String> withoutRoom = List.of();
            if (parts.contains(YearCopyPart.CLASSES)) {
                CopyClassesResponse classes = classManagementService.copyClasses(
                        currentUser, target.getId(), new CopyClassesRequest(source.getId(), null));
                copiedClasses = classes.copied();
                withoutRoom = classes.withoutRoom();
                classes.skipped()
                        .forEach(s ->
                                skipped.add(new YearCopyResponse.Skipped(YearCopyPart.CLASSES, s.name(), s.reason())));
            }
            int teacherClasses =
                    parts.contains(YearCopyPart.TEACHERS) ? copyTeachers(currentUser, source, target, skipped) : 0;
            int teamLeaders = parts.contains(YearCopyPart.TEAM_LEADERS)
                    ? copyTeamLeaders(currentUser, source, target, skipped)
                    : 0;
            LocalTime cutoff =
                    parts.contains(YearCopyPart.CUTOFF) ? copyCutoff(currentUser, source, target, skipped) : null;
            boolean semesterCopied =
                    parts.contains(YearCopyPart.SEMESTER) && copySemester(currentUser, source, target, skipped);
            YearCopyResponse response = new YearCopyResponse(
                    copiedClasses, withoutRoom, teacherClasses, teamLeaders, cutoff, semesterCopied, skipped);
            log.info(
                    "{}|END|classes={}|teacherClasses={}|teamLeaders={}|skipped={}",
                    logPrefix,
                    copiedClasses.size(),
                    teacherClasses,
                    teamLeaders,
                    skipped.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** GV theo lớp cùng tên + cùng điểm trường; GV đã nghỉ / khóa / không còn ở điểm trường bị bỏ (kèm lý do). */
    private int copyTeachers(
            CurrentUser currentUser, SchoolYear source, SchoolYear target, List<YearCopyResponse.Skipped> skipped) {
        LocalDate today = schoolConfigAccess.today();
        List<Classroom> sourceClasses =
                classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(source.getId());
        List<Classroom> targetClasses =
                classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(target.getId());
        if (sourceClasses.isEmpty() || targetClasses.isEmpty()) {
            return 0;
        }
        Map<String, Classroom> targetByKey = targetClasses.stream()
                .collect(Collectors.toMap(YearCopyServiceImpl::classKey, Function.identity(), (a, b) -> a));
        Set<Long> targetWithTeachers = staffAssignmentRepository.findCurrentTeachers(ids(targetClasses), today).stream()
                .map(StaffAssignment::getClassroomId)
                .collect(Collectors.toSet());
        Map<Long, List<StaffAssignment>> sourceTeachers =
                staffAssignmentRepository.findCurrentTeachers(ids(sourceClasses), asOf(source, today)).stream()
                        .collect(Collectors.groupingBy(StaffAssignment::getClassroomId));
        Set<Long> userIds = sourceTeachers.values().stream()
                .flatMap(List::stream)
                .map(StaffAssignment::getUserId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return 0;
        }
        Set<Long> inactive = new HashSet<>(staffAssignmentRepository.findInactiveUserIds(userIds));
        Map<Long, String> names = names(userIds);
        Map<Long, TeacherCandidateResponse> directory = teacherDirectory.findTeachers(today).stream()
                .collect(Collectors.toMap(TeacherCandidateResponse::id, Function.identity(), (a, b) -> a));
        int copied = 0;
        for (Classroom sourceClass : sourceClasses) {
            Classroom targetClass = targetByKey.get(classKey(sourceClass));
            List<StaffAssignment> rows = sourceTeachers.getOrDefault(sourceClass.getId(), List.of());
            if (targetClass == null || rows.isEmpty() || targetWithTeachers.contains(targetClass.getId())) {
                continue;
            }
            Map<Long, Boolean> teachers = new LinkedHashMap<>();
            rows.forEach(a -> teachers.merge(a.getUserId(), a.isHomeroom(), Boolean::logicalOr));
            List<Long> kept = new ArrayList<>();
            for (Long userId : teachers.keySet()) {
                TeacherCandidateResponse candidate = directory.get(userId);
                if (inactive.contains(userId)) {
                    skipped.add(new YearCopyResponse.Skipped(
                            YearCopyPart.TEACHERS,
                            targetClass.getName(),
                            "Giáo viên " + names.get(userId) + " đã khóa tài khoản / nghỉ việc"));
                } else if (candidate == null || !candidate.campusIds().contains(targetClass.getCampusId())) {
                    skipped.add(new YearCopyResponse.Skipped(
                            YearCopyPart.TEACHERS,
                            targetClass.getName(),
                            "Giáo viên " + names.get(userId) + " không còn thuộc điểm trường của lớp"));
                } else {
                    kept.add(userId);
                }
            }
            if (kept.isEmpty()) {
                continue;
            }
            Long homeroom = kept.stream().filter(teachers::get).findFirst().orElse(null);
            try {
                teacherAssignmentService.assignClassTeachers(
                        currentUser, targetClass.getId(), new ClassTeachersRequest(kept, homeroom));
                copied++;
            } catch (GlobalException ex) {
                skipped.add(
                        new YearCopyResponse.Skipped(YearCopyPart.TEACHERS, targetClass.getName(), ex.getMessage()));
            }
        }
        return copied;
    }

    /** Tổ trưởng theo nhóm tuổi (nhóm tuổi dùng chung mọi năm); nhóm tuổi năm đích đã có tổ trưởng thì giữ. */
    private int copyTeamLeaders(
            CurrentUser currentUser, SchoolYear source, SchoolYear target, List<YearCopyResponse.Skipped> skipped) {
        LocalDate today = schoolConfigAccess.today();
        LocalDate targetFrom = today.isAfter(target.getStartDate()) ? today : target.getStartDate();
        Set<Long> targetGroups =
                staffAssignmentRepository
                        .findCurrentTeamLeaders(target.getId(), target.getEndDate(), targetFrom)
                        .stream()
                        .map(StaffAssignment::getAgeGroupId)
                        .collect(Collectors.toSet());
        Map<Long, Long> sourceLeaders = new LinkedHashMap<>();
        staffAssignmentRepository
                .findCurrentTeamLeaders(source.getId(), source.getEndDate(), asOf(source, today))
                .stream()
                .filter(a -> a.getAgeGroupId() != null && !targetGroups.contains(a.getAgeGroupId()))
                .forEach(a -> sourceLeaders.put(a.getAgeGroupId(), a.getUserId()));
        if (sourceLeaders.isEmpty()) {
            return 0;
        }
        Set<Long> inactive = new HashSet<>(staffAssignmentRepository.findInactiveUserIds(sourceLeaders.values()));
        Map<Long, String> names = names(sourceLeaders.values());
        Map<Long, String> groupNames = ageGroupRepository.findAllById(sourceLeaders.keySet()).stream()
                .collect(Collectors.toMap(AgeGroup::getId, AgeGroup::getName));
        int copied = 0;
        for (Map.Entry<Long, Long> leader : sourceLeaders.entrySet()) {
            String groupName = groupNames.getOrDefault(leader.getKey(), "#" + leader.getKey());
            if (inactive.contains(leader.getValue())) {
                skipped.add(new YearCopyResponse.Skipped(
                        YearCopyPart.TEAM_LEADERS,
                        groupName,
                        "Giáo viên " + names.get(leader.getValue()) + " đã khóa tài khoản / nghỉ việc"));
                continue;
            }
            try {
                teacherAssignmentService.assignTeamLeader(
                        currentUser, new TeamLeaderRequest(target.getId(), leader.getKey(), leader.getValue()));
                copied++;
            } catch (GlobalException ex) {
                skipped.add(new YearCopyResponse.Skipped(YearCopyPart.TEAM_LEADERS, groupName, ex.getMessage()));
            }
        }
        return copied;
    }

    /** Giờ chốt mới nhất của năm nguồn, áp dụng từ ngày đầu năm đích (hoặc hôm nay nếu năm đích đã qua ngày đầu). */
    private LocalTime copyCutoff(
            CurrentUser currentUser, SchoolYear source, SchoolYear target, List<YearCopyResponse.Skipped> skipped) {
        if (!attendanceCutoffRepository
                .findAllBySchoolYearIdOrderByIdDesc(target.getId())
                .isEmpty()) {
            return null;
        }
        List<AttendanceCutoff> history = attendanceCutoffRepository.findAllBySchoolYearIdOrderByIdDesc(source.getId());
        if (history.isEmpty()) {
            skipped.add(new YearCopyResponse.Skipped(
                    YearCopyPart.CUTOFF, source.getName(), "Năm học nguồn chưa có giờ chốt điểm danh"));
            return null;
        }
        LocalTime time = history.getFirst().getCutoffTime();
        LocalDate today = schoolConfigAccess.today();
        LocalDate from = today.isAfter(target.getStartDate()) ? today : target.getStartDate();
        try {
            attendanceCutoffService.saveCutoff(
                    currentUser,
                    target.getId(),
                    new CutoffRequest(time, from, "Sao chép từ năm học " + source.getName()));
            return time;
        } catch (GlobalException ex) {
            skipped.add(new YearCopyResponse.Skipped(YearCopyPart.CUTOFF, source.getName(), ex.getMessage()));
            return null;
        }
    }

    /** Chép 4 ngày học kỳ, cùng ngày/tháng, dời theo số năm giữa hai năm học. */
    private boolean copySemester(
            CurrentUser currentUser, SchoolYear source, SchoolYear target, List<YearCopyResponse.Skipped> skipped) {
        if (target.getSemester1Start() != null) {
            return false;
        }
        if (source.getSemester1Start() == null) {
            skipped.add(new YearCopyResponse.Skipped(
                    YearCopyPart.SEMESTER, source.getName(), "Năm học nguồn chưa có thời gian học kỳ"));
            return false;
        }
        long years = ChronoUnit.YEARS.between(
                source.getStartDate().withDayOfYear(1), target.getStartDate().withDayOfYear(1));
        SchoolYearSemesterRequest request = new SchoolYearSemesterRequest(
                source.getSemester1Start().plusYears(years),
                source.getSemester1End().plusYears(years),
                source.getSemester2Start().plusYears(years),
                source.getSemester2End().plusYears(years));
        try {
            schoolYearService.updateSemester(currentUser, target.getId(), request);
            return true;
        } catch (GlobalException ex) {
            skipped.add(new YearCopyResponse.Skipped(YearCopyPart.SEMESTER, source.getName(), ex.getMessage()));
            return false;
        }
    }

    // Năm nguồn đã kết thúc: lấy phân công còn hiệu lực ở ngày cuối năm; năm đang chạy: hôm nay
    private static LocalDate asOf(SchoolYear year, LocalDate today) {
        if (today.isBefore(year.getStartDate())) {
            return year.getStartDate();
        }
        return today.isAfter(year.getEndDate()) ? year.getEndDate() : today;
    }

    private static String classKey(Classroom c) {
        return c.getCampusId() + "|"
                + (c.getName() == null ? "" : c.getName().trim().toLowerCase(Locale.ROOT));
    }

    private static List<Long> ids(List<Classroom> classes) {
        return classes.stream().map(Classroom::getId).toList();
    }

    private Map<Long, String> names(Collection<Long> userIds) {
        Map<Long, String> names = roomRepository.findUserNames(userIds).stream()
                .collect(Collectors.toMap(
                        RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName, (a, b) -> a));
        userIds.stream().filter(Objects::nonNull).forEach(id -> names.putIfAbsent(id, "#" + id));
        return names;
    }

    private SchoolYear findYear(Long id) {
        return schoolYearRepository.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }
}
