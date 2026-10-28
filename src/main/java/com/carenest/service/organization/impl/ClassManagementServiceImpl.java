package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassRequest;
import com.carenest.dto.organization.ClassResponse;
import com.carenest.dto.organization.ClassTeachersResponse;
import com.carenest.dto.organization.CopyClassesRequest;
import com.carenest.dto.organization.CopyClassesResponse;
import com.carenest.dto.organization.SchoolStructureResponse;
import com.carenest.dto.organization.TeacherCandidateResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.Room;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.TeacherDirectory;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClassManagementServiceImpl implements ClassManagementService {
    private static final String LOG_PREFIX = "[ClassManagementServiceImpl]";
    // Mỗi lớp 2 giáo viên (FE TEACHERS_PER_CLASS)
    private static final int TEACHERS_PER_CLASS = 2;

    private final ClassroomRepository classroomRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final CampusRepository campusRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final RoomRepository roomRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final TeacherDirectory teacherDirectory;
    private final SchoolConfigAccess schoolConfigAccess;
    private final SchoolStructureMapper schoolStructureMapper;
    private final YearSnapshotReader yearSnapshotReader;

    @Override
    @Transactional(readOnly = true)
    public SchoolStructureResponse getSchoolStructure(CurrentUser currentUser, Long schoolYearId, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getSchoolStructure|userId=" + currentUser.userId() + "|schoolYearId="
                + schoolYearId + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            SchoolYear year = schoolYearId == null
                    ? schoolYearRepository
                            .findFirstByStatus(SchoolYearStatus.ACTIVE)
                            .orElseThrow(() -> GlobalException.of(ApiCode.NO_ACTIVE_SCHOOL_YEAR))
                    : findYear(schoolYearId);
            List<Classroom> classes = classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(year.getId());
            classes = filterVisible(currentUser, classes);
            if (campusId != null) {
                classes = classes.stream()
                        .filter(c -> c.getCampusId().equals(campusId))
                        .toList();
            }
            List<ClassResponse> classResponses = toResponses(classes);
            // CFG-YR-02: năm đã kết thúc ⇒ nhóm tuổi như lúc kết thúc năm
            List<AgeGroupDetailResponse> ageGroups = yearSnapshotReader
                    .ageGroups(year.getId())
                    .orElseGet(() -> ageGroupRepository.findAll().stream()
                            .sorted(Comparator.comparing((AgeGroup g) ->
                                            g.getAgeFromMonths() == null ? Short.MAX_VALUE : g.getAgeFromMonths())
                                    .thenComparing(AgeGroup::getSortOrder))
                            .map(schoolStructureMapper::toDetailResponse)
                            .toList());
            Map<Long, TeacherCandidateResponse> teachersById =
                    teacherDirectory.findTeachers(schoolConfigAccess.today()).stream()
                            .collect(Collectors.toMap(TeacherCandidateResponse::id, t -> t));
            LocalDate leaderFrom = schoolConfigAccess.today().isAfter(year.getStartDate())
                    ? schoolConfigAccess.today()
                    : year.getStartDate();
            List<SchoolStructureResponse.TeamLeader> teamLeaders =
                    staffAssignmentRepository
                            .findCurrentTeamLeaders(year.getId(), year.getEndDate(), leaderFrom)
                            .stream()
                            .map(a -> {
                                TeacherCandidateResponse teacher = teachersById.get(a.getUserId());
                                return new SchoolStructureResponse.TeamLeader(
                                        a.getAgeGroupId(),
                                        a.getUserId(),
                                        teacher == null ? null : teacher.fullName(),
                                        teacher == null ? null : teacher.campusId());
                            })
                            .toList();
            boolean plannedYear = year.getStatus() == SchoolYearStatus.PLANNED;
            Map<Long, SchoolStructureResponse.GroupStat> groupStats = ageGroups.stream()
                    .collect(Collectors.toMap(AgeGroupDetailResponse::id, g -> {
                        List<ClassResponse> list = classResponses.stream()
                                .filter(c -> g.id().equals(c.ageGroupId()))
                                .toList();
                        return new SchoolStructureResponse.GroupStat(
                                list.size(),
                                list.stream()
                                        .mapToLong(ClassResponse::childCount)
                                        .sum(),
                                plannedYear
                                        ? list.stream()
                                                .map(ClassResponse::plannedCount)
                                                .filter(Objects::nonNull)
                                                .mapToLong(Long::longValue)
                                                .sum()
                                        : null,
                                list.stream()
                                        .map(ClassResponse::capacity)
                                        .filter(Objects::nonNull)
                                        .mapToLong(Integer::longValue)
                                        .sum());
                    }));
            SchoolStructureResponse response = new SchoolStructureResponse(
                    schoolStructureMapper.toDetailResponse(year, classroomRepository.countBySchoolYearId(year.getId())),
                    ageGroups,
                    classResponses,
                    teamLeaders,
                    groupStats);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ClassResponse createClass(CurrentUser currentUser, ClassRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createClass|userId=" + currentUser.userId() + "|schoolYearId=" + request.schoolYearId();
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYearRules.checkOpen(findYear(request.schoolYearId()));
            String name = validate(request, null, 0);
            Classroom classroom = Classroom.create(request.schoolYearId(), request.campusId());
            classroom.changeInfo(
                    name,
                    request.campusId(),
                    request.ageGroupId(),
                    request.locationId(),
                    request.capacity().shortValue());
            classroom = classroomRepository.save(classroom);
            ClassResponse response = toResponses(List.of(classroom)).getFirst();
            log.info("{}|classId={}|END", logPrefix, classroom.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ClassResponse updateClass(CurrentUser currentUser, Long classId, ClassRequest request) {
        String logPrefix = LOG_PREFIX + "|updateClass|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            Classroom classroom = findClass(classId);
            if (!classroom.getSchoolYearId().equals(request.schoolYearId())) {
                throw GlobalException.of(ApiCode.CLASS_YEAR_IMMUTABLE);
            }
            SchoolYearRules.checkOpen(findYear(classroom.getSchoolYearId()));
            long enrolled = activeChildren(List.of(classId)).getOrDefault(classId, 0L);
            long teachers = activeTeachers(List.of(classId)).getOrDefault(classId, 0L);
            long planned = plannedChildren(List.of(classId)).getOrDefault(classId, 0L);
            // Lớp đã có trẻ: không đổi nhóm tuổi/điểm trường; lưu lớp không xếp hay chuyển trẻ
            if (enrolled > 0 && !Objects.equals(classroom.getAgeGroupId(), request.ageGroupId())) {
                throw GlobalException.of(ApiCode.CLASS_AGE_GROUP_LOCKED);
            }
            if ((enrolled > 0 || teachers > 0) && !classroom.getCampusId().equals(request.campusId())) {
                throw GlobalException.of(ApiCode.CLASS_CAMPUS_LOCKED);
            }
            // Lớp năm mới đã có trẻ xếp vào: giữ nhóm tuổi, điểm trường; sĩ số không thấp hơn số trẻ đã xếp
            if (planned > 0 && !Objects.equals(classroom.getAgeGroupId(), request.ageGroupId())) {
                throw GlobalException.of(ApiCode.CLASS_PLACED_AGE_GROUP_LOCKED);
            }
            if (planned > 0 && !classroom.getCampusId().equals(request.campusId())) {
                throw GlobalException.of(ApiCode.CLASS_PLACED_CAMPUS_LOCKED);
            }
            String name = validate(request, classId, Math.max(enrolled, planned));
            classroom.changeInfo(
                    name,
                    request.campusId(),
                    request.ageGroupId(),
                    request.locationId(),
                    request.capacity().shortValue());
            classroomRepository.flush();
            ClassResponse response = toResponses(List.of(classroom)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteClass(CurrentUser currentUser, Long classId) {
        String logPrefix = LOG_PREFIX + "|deleteClass|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            Classroom classroom = findClass(classId);
            SchoolYearRules.checkOpen(findYear(classroom.getSchoolYearId()));
            if (classroomRepository.countAllEnrollments(classId) > 0) {
                throw GlobalException.of(ApiCode.CLASS_HAS_DEPENDENTS, "trẻ");
            }
            if (plannedChildren(List.of(classId)).getOrDefault(classId, 0L) > 0) {
                throw GlobalException.of(ApiCode.CLASS_HAS_PLACED_CHILDREN);
            }
            try {
                classroomRepository.delete(classroom);
                classroomRepository.flush();
            } catch (DataIntegrityViolationException integrityEx) {
                // Phân công giáo viên, điểm danh, đánh giá... còn tham chiếu lớp
                throw GlobalException.withCause(ApiCode.CLASS_HAS_DEPENDENTS, integrityEx, "dữ liệu liên quan");
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CopyClassesResponse copyClasses(CurrentUser currentUser, Long targetYearId, CopyClassesRequest request) {
        String logPrefix = LOG_PREFIX + "|copyClasses|userId=" + currentUser.userId() + "|targetYearId=" + targetYearId
                + "|sourceYearId=" + request.sourceYearId();
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYear target = findYear(targetYearId);
            SchoolYearRules.checkOpen(target);
            SchoolYear source = findYear(request.sourceYearId());
            if (source.getId().equals(target.getId())) {
                throw GlobalException.of(ApiCode.COPY_SOURCE_YEAR_INVALID);
            }
            Set<Long> picked =
                    request.classIds() == null || request.classIds().isEmpty() ? null : Set.copyOf(request.classIds());
            List<Classroom> sourceClasses =
                    classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(source.getId()).stream()
                            .filter(c -> picked == null || picked.contains(c.getId()))
                            .toList();
            Map<Long, AgeGroup> ageGroups = ageGroupRepository.findAll().stream()
                    .collect(Collectors.toMap(AgeGroup::getId, Function.identity()));
            Map<Long, Room> rooms = roomRepository
                    .findAllById(sourceClasses.stream()
                            .map(Classroom::getRoomId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(Room::getId, Function.identity()));
            List<String> copied = new ArrayList<>();
            List<CopyClassesResponse.Skipped> skipped = new ArrayList<>();
            List<String> withoutRoom = new ArrayList<>();
            // Chỉ sao chép tên, nhóm tuổi, điểm trường, phòng, sĩ số — không bao giờ sao chép giáo viên hay trẻ
            for (Classroom c : sourceClasses) {
                if (!campusRepository.existsByIdAndStatus(c.getCampusId(), CampusStatus.ACTIVE)) {
                    skipped.add(new CopyClassesResponse.Skipped(c.getName(), "Campus is inactive"));
                    continue;
                }
                if (classroomRepository.existsName(target.getId(), c.getCampusId(), c.getName(), null)) {
                    skipped.add(new CopyClassesResponse.Skipped(c.getName(), "Campus already has this class name"));
                    continue;
                }
                AgeGroup group = c.getAgeGroupId() == null ? null : ageGroups.get(c.getAgeGroupId());
                if (group == null) {
                    skipped.add(new CopyClassesResponse.Skipped(c.getName(), "Age group no longer exists"));
                    continue;
                }
                Room room = c.getRoomId() == null ? null : rooms.get(c.getRoomId());
                boolean roomFree = room != null
                        && room.getStatus() == RoomStatus.ACTIVE
                        && !classroomRepository.existsRoomInYear(target.getId(), room.getId(), null);
                if (room != null && !roomFree) {
                    withoutRoom.add(c.getName());
                }
                int capacity = c.getCapacity() == null ? Short.MAX_VALUE : c.getCapacity();
                if (roomFree && room.getCapacity() != null) {
                    capacity = Math.min(capacity, room.getCapacity());
                }
                if (group.getMaxClassSize() != null) {
                    capacity = Math.min(capacity, group.getMaxClassSize());
                }
                Classroom copy = Classroom.create(target.getId(), c.getCampusId());
                copy.changeInfo(
                        c.getName(), c.getCampusId(), group.getId(), roomFree ? room.getId() : null, (short) capacity);
                classroomRepository.saveAndFlush(copy);
                copied.add(c.getName());
            }
            CopyClassesResponse response = new CopyClassesResponse(copied, skipped, withoutRoom);
            log.info("{}|copied={}|END", logPrefix, copied.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Kiểm tra lớp mới/sửa: tên không trùng trong điểm trường + năm, phòng cùng điểm trường và chưa có lớp khác,
    // sĩ số trong [max(1, số trẻ), min(sĩ số tối đa nhóm tuổi, sức chứa phòng)]
    private String validate(ClassRequest request, Long classId, long enrolled) {
        String name = request.name().trim();
        if (!campusRepository.existsById(request.campusId())) {
            throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
        }
        if (!campusRepository.existsByIdAndStatus(request.campusId(), CampusStatus.ACTIVE)) {
            throw GlobalException.of(ApiCode.CAMPUS_INACTIVE);
        }
        AgeGroup ageGroup = ageGroupRepository
                .findById(request.ageGroupId())
                .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
        Room room = roomRepository
                .findById(request.locationId())
                .filter(r -> r.getStatus() != RoomStatus.DELETED)
                .orElseThrow(() -> GlobalException.of(ApiCode.ROOM_NOT_FOUND));
        if (!room.getCampusId().equals(request.campusId())) {
            throw GlobalException.of(ApiCode.ROOM_NOT_IN_CAMPUS);
        }
        Long currentRoomId = classId == null ? null : findClass(classId).getRoomId();
        if (room.getStatus() != RoomStatus.ACTIVE && !room.getId().equals(currentRoomId)) {
            throw GlobalException.of(ApiCode.ROOM_INACTIVE);
        }
        if (classroomRepository.existsName(request.schoolYearId(), request.campusId(), name, classId)) {
            throw GlobalException.of(ApiCode.CLASS_NAME_EXISTS);
        }
        if (classroomRepository.existsRoomInYear(request.schoolYearId(), room.getId(), classId)) {
            throw GlobalException.of(ApiCode.ROOM_TAKEN);
        }
        int max = Integer.MAX_VALUE;
        if (ageGroup.getMaxClassSize() != null) {
            max = Math.min(max, ageGroup.getMaxClassSize());
        }
        if (room.getCapacity() != null) {
            max = Math.min(max, room.getCapacity());
        }
        long min = Math.max(1, enrolled);
        if (request.capacity() < min || request.capacity() > max) {
            throw GlobalException.of(ApiCode.CLASS_MAX_SIZE_OUT_OF_RANGE, min, max);
        }
        return name;
    }

    private List<Classroom> filterVisible(CurrentUser currentUser, List<Classroom> classes) {
        if (schoolConfigAccess.isPrincipal(currentUser)) {
            return classes;
        }
        Set<Long> campusIds = schoolConfigAccess.vicePrincipalCampusIds(currentUser);
        Set<Long> classIds = schoolConfigAccess.teacherClassroomIds(currentUser);
        if (campusIds.isEmpty() && classIds.isEmpty()) {
            throw GlobalException.of(ApiCode.SCHOOL_CONFIG_ACCESS_DENIED);
        }
        return classes.stream()
                .filter(c -> campusIds.contains(c.getCampusId()) || classIds.contains(c.getId()))
                .toList();
    }

    private List<ClassResponse> toResponses(List<Classroom> classes) {
        if (classes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = classes.stream().map(Classroom::getId).toList();
        Map<Long, Long> children = activeChildren(ids);
        Map<Long, List<StaffAssignment>> teachers =
                staffAssignmentRepository.findCurrentTeachers(ids, schoolConfigAccess.today()).stream()
                        .collect(Collectors.groupingBy(StaffAssignment::getClassroomId));
        Map<Long, Long> planned = plannedChildren(ids);
        Set<Long> plannedYears =
                schoolYearRepository
                        .findAllById(
                                classes.stream().map(Classroom::getSchoolYearId).collect(Collectors.toSet()))
                        .stream()
                        .filter(y -> y.getStatus() == SchoolYearStatus.PLANNED)
                        .map(SchoolYear::getId)
                        .collect(Collectors.toSet());
        Set<Long> teacherUserIds = teachers.values().stream()
                .flatMap(List::stream)
                .map(StaffAssignment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> teacherNames = teacherUserIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(teacherUserIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        Map<Long, Room> rooms = roomRepository
                .findAllById(classes.stream()
                        .map(Classroom::getRoomId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Room::getId, r -> r));
        // Năm đã kết thúc: mã/tên phòng theo bản chụp
        Map<Long, Map<Long, SchoolYearRepository.RoomSnapshot>> roomSnapshots = new HashMap<>();
        return classes.stream()
                .map(c -> {
                    long childCount = children.getOrDefault(c.getId(), 0L);
                    Room room = c.getRoomId() == null ? null : rooms.get(c.getRoomId());
                    SchoolYearRepository.RoomSnapshot frozen = c.getRoomId() == null
                            ? null
                            : roomSnapshots
                                    .computeIfAbsent(c.getSchoolYearId(), y -> {
                                        Map<Long, SchoolYearRepository.RoomSnapshot> m = yearSnapshotReader.rooms(y);
                                        return m == null ? Map.of() : m;
                                    })
                                    .get(c.getRoomId());
                    // Năm sắp diễn ra: trẻ đã xếp lớp chỉ là dự kiến, vào lớp khi kích hoạt năm học
                    Long plannedCount =
                            plannedYears.contains(c.getSchoolYearId()) ? planned.getOrDefault(c.getId(), 0L) : null;
                    Integer capacity =
                            c.getCapacity() == null ? null : c.getCapacity().intValue();
                    return new ClassResponse(
                            c.getId(),
                            c.getName(),
                            c.getSchoolYearId(),
                            c.getCampusId(),
                            c.getAgeGroupId(),
                            c.getRoomId(),
                            frozen != null ? frozen.getCode() : room == null ? null : room.getCode(),
                            frozen != null ? frozen.getName() : room == null ? null : room.getName(),
                            capacity,
                            c.getStatus(),
                            childCount,
                            plannedCount,
                            (int) Math.max(
                                    0,
                                    TEACHERS_PER_CLASS
                                            - teacherIds(teachers, c.getId()).size()),
                            teacherIds(teachers, c.getId()),
                            teachers.getOrDefault(c.getId(), List.of()).stream()
                                    .filter(StaffAssignment::isHomeroom)
                                    .map(StaffAssignment::getUserId)
                                    .findFirst()
                                    .orElse(null),
                            c.getRoomId() == null,
                            fill(childCount + (plannedCount == null ? 0 : plannedCount), capacity),
                            teacherIds(teachers, c.getId()).stream()
                                    .map(id -> new ClassTeachersResponse.Teacher(id, teacherNames.get(id)))
                                    .toList());
                })
                .toList();
    }

    private static List<Long> teacherIds(Map<Long, List<StaffAssignment>> teachers, Long classId) {
        return teachers.getOrDefault(classId, List.of()).stream()
                .map(StaffAssignment::getUserId)
                .distinct()
                .toList();
    }

    private static String fill(long childCount, Integer capacity) {
        if (capacity == null) {
            return "OPEN";
        }
        if (childCount >= capacity) {
            return "FULL";
        }
        return childCount >= capacity * 0.9 ? "NEAR" : "OPEN";
    }

    private Map<Long, Long> activeChildren(Collection<Long> classIds) {
        return classroomRepository.countActiveChildren(classIds, schoolConfigAccess.today()).stream()
                .collect(Collectors.toMap(
                        ClassroomRepository.ClassroomCount::getClassroomId,
                        ClassroomRepository.ClassroomCount::getTotal));
    }

    private Map<Long, Long> plannedChildren(Collection<Long> classIds) {
        return classroomRepository.countPlannedChildren(classIds).stream()
                .collect(Collectors.toMap(
                        ClassroomRepository.ClassroomCount::getClassroomId,
                        ClassroomRepository.ClassroomCount::getTotal));
    }

    private Map<Long, Long> activeTeachers(Collection<Long> classIds) {
        return classroomRepository.countActiveTeachers(classIds, schoolConfigAccess.today()).stream()
                .collect(Collectors.toMap(
                        ClassroomRepository.ClassroomCount::getClassroomId,
                        ClassroomRepository.ClassroomCount::getTotal));
    }

    private SchoolYear findYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }

    private Classroom findClass(Long classId) {
        return classroomRepository.findById(classId).orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
    }
}
