package com.carenest.service.organization.impl;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.dto.organization.StaffBriefResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Campus;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.CampusMapper;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class CampusServiceImpl implements CampusService {
    private static final String LOG_PREFIX = "[CampusServiceImpl]";

    private final CampusRepository campusRepository;
    private final ClassroomRepository classroomRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final RoomRepository roomRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final SchoolStructureMapper schoolStructureMapper;
    private final StaffAssignmentService staffAssignmentService;
    private final CampusMapper campusMapper;
    private final YearSnapshotReader yearSnapshotReader;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CampusResponse> getCampuses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getCampuses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            List<Campus> campuses = isPrincipal(currentUser, today)
                    ? campusRepository.findAllByOrderByMainDescCodeAsc()
                    : campusRepository.findAllByIdInOrderByMainDescCodeAsc(vicePrincipalCampusIds(currentUser, today));
            Long yearId = resolveSchoolYearId(schoolYearId);
            Map<Long, Long> classCounts =
                    yearId == null ? Map.of() : toMap(campusRepository.countClassesByCampus(yearId));
            Map<Long, Long> childCounts =
                    yearId == null ? Map.of() : toMap(campusRepository.countChildrenByCampus(yearId, today));
            Map<Long, List<StaffBriefResponse>> vicePrincipals = campusStaff(today, StaffRole.VICE_PRINCIPAL);
            Map<Long, SchoolYearRepository.CampusSnapshot> snapshot = campusSnapshot(yearId);
            List<CampusResponse> response = campuses.stream()
                    // Năm đã kết thúc: chỉ điểm trường có lúc kết thúc năm, thông tin như bản chụp
                    .filter(c -> snapshot == null || snapshot.containsKey(c.getId()))
                    .map(c -> withSnapshot(
                            campusMapper.toResponse(
                                    c,
                                    classCounts.getOrDefault(c.getId(), 0L),
                                    childCounts.getOrDefault(c.getId(), 0L),
                                    vicePrincipals.getOrDefault(c.getId(), List.of())),
                            snapshot))
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CampusDetailResponse getCampusDetail(CurrentUser currentUser, Long campusId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getCampusDetail|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Campus campus = findCampus(campusId);
            // AUTH-02: PHT chỉ xem điểm trường được phân công
            if (!isPrincipal(currentUser, today)
                    && !vicePrincipalCampusIds(currentUser, today).contains(campusId)) {
                throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
            }
            Long yearId = resolveSchoolYearId(schoolYearId);
            List<Classroom> yearClasses = yearId == null
                    ? List.of()
                    : classroomRepository.findAllByCampusIdAndSchoolYearIdOrderByNameAsc(campusId, yearId);
            Map<Long, Long> classChildren = yearClasses.isEmpty()
                    ? Map.of()
                    : classroomRepository
                            .countActiveChildren(
                                    yearClasses.stream().map(Classroom::getId).toList(), today)
                            .stream()
                            .collect(Collectors.toMap(
                                    ClassroomRepository.ClassroomCount::getClassroomId,
                                    ClassroomRepository.ClassroomCount::getTotal));
            // Chủ nhiệm từng lớp (kèm tên, để năm đã kết thúc vẫn hiện đúng người)
            Map<Long, Long> homerooms = yearClasses.isEmpty()
                    ? Map.of()
                    : staffAssignmentRepository
                            .findCurrentTeachers(
                                    yearClasses.stream().map(Classroom::getId).toList(), today)
                            .stream()
                            .filter(StaffAssignment::isHomeroom)
                            .collect(Collectors.toMap(
                                    StaffAssignment::getClassroomId, StaffAssignment::getUserId, (a, b) -> a));
            Map<Long, String> homeroomNames = homerooms.isEmpty()
                    ? Map.of()
                    : roomRepository.findUserNames(Set.copyOf(homerooms.values())).stream()
                            .collect(Collectors.toMap(
                                    RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
            List<CampusDetailResponse.ClassBrief> classes = yearClasses.stream()
                    .map(c -> new CampusDetailResponse.ClassBrief(
                            c.getId(),
                            c.getName(),
                            c.getAgeGroupId(),
                            c.getCapacity() == null ? null : c.getCapacity().intValue(),
                            c.getRoomId(),
                            classChildren.getOrDefault(c.getId(), 0L),
                            homerooms.get(c.getId()),
                            homerooms.containsKey(c.getId()) ? homeroomNames.get(homerooms.get(c.getId())) : null))
                    .toList();
            List<CampusDetailResponse.KitchenRoom> kitchenRooms = roomRepository
                    .findAllByCampusIdInAndStatusNotOrderByCampusIdAscCodeAsc(Set.of(campusId), RoomStatus.DELETED)
                    .stream()
                    .filter(r -> r.getRoomType() == RoomType.KITCHEN)
                    .map(r -> new CampusDetailResponse.KitchenRoom(
                            r.getId(),
                            r.getCode(),
                            r.getName(),
                            r.getCapacity() == null ? null : r.getCapacity().intValue(),
                            r.getStatus()))
                    .toList();
            long childCount = yearId == null
                    ? 0
                    : toMap(campusRepository.countChildrenByCampus(yearId, today))
                            .getOrDefault(campusId, 0L);
            List<StaffBriefResponse> vicePrincipals =
                    campusStaff(today, StaffRole.VICE_PRINCIPAL).getOrDefault(campusId, List.of());
            List<StaffBriefResponse> kitchenStaff =
                    campusStaff(today, StaffRole.KITCHEN_STAFF).getOrDefault(campusId, List.of());
            List<StaffBriefResponse> teachers = yearId == null
                    ? List.of()
                    : campusRepository.findActiveClassroomStaff(campusId, yearId, today).stream()
                            .filter(s -> StaffRole.TEACHER.name().equals(s.getStaffRole()))
                            .map(s -> new StaffBriefResponse(s.getUserId(), s.getFullName()))
                            .distinct()
                            .toList();
            CampusDetailResponse response = new CampusDetailResponse(
                    withSnapshot(
                            campusMapper.toResponse(campus, classes.size(), childCount, vicePrincipals),
                            campusSnapshot(yearId)),
                    yearId,
                    classes,
                    yearSnapshotReader
                            .ageGroups(yearId)
                            .orElseGet(() -> ageGroupRepository.findAll().stream()
                                    .sorted(Comparator.comparing((AgeGroup g) -> g.getAgeFromMonths() == null
                                                    ? Short.MAX_VALUE
                                                    : g.getAgeFromMonths())
                                            .thenComparing(AgeGroup::getSortOrder))
                                    .map(schoolStructureMapper::toDetailResponse)
                                    .toList()),
                    vicePrincipals,
                    teachers,
                    new CampusDetailResponse.Kitchen(kitchenRooms, kitchenStaff));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse createCampus(CurrentUser currentUser, CampusRequest request) {
        String logPrefix = LOG_PREFIX + "|createCampus|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            CampusRequest data = normalize(request);
            if (campusRepository.existsByCodeIgnoreCase(data.code())) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus code already exists");
            }
            if (campusRepository.existsByNameIgnoreCase(data.name())) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus name already exists");
            }
            Campus campus = Campus.create(data.code(), data.name(), data.shortName(), data.address(), data.phone());
            // Trường có đúng một điểm trường chính; điểm trường đầu tiên luôn là điểm chính
            boolean hasMain = campusRepository.findFirstByMainTrue().isPresent();
            if (Boolean.TRUE.equals(data.isMain()) || !hasMain) {
                unsetCurrentMain();
                campus.markMain(true);
            }
            campus = campusRepository.save(campus);
            log.info("{}|campusId={}|END", logPrefix, campus.getId());
            return campusMapper.toResponse(campus, 0, 0, List.of());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse updateCampus(CurrentUser currentUser, Long campusId, CampusRequest request) {
        String logPrefix = LOG_PREFIX + "|updateCampus|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            CampusRequest data = normalize(request);
            if (campusRepository.existsByCodeIgnoreCaseAndIdNot(data.code(), campusId)) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus code already exists");
            }
            if (campusRepository.existsByNameIgnoreCaseAndIdNot(data.name(), campusId)) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus name already exists");
            }
            if (campus.isMain() && Boolean.FALSE.equals(data.isMain())) {
                throw new GlobalException(
                        ApiCode.CONFLICT, "The school needs one main campus; choose another main campus first");
            }
            campus.changeInfo(data.code(), data.name(), data.shortName(), data.address(), data.phone());
            if (Boolean.TRUE.equals(data.isMain()) && !campus.isMain()) {
                unsetCurrentMain();
                campus.markMain(true);
            }
            campusRepository.flush();
            CampusResponse response = toResponseWithStats(campus);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteCampus(CurrentUser currentUser, Long campusId) {
        String logPrefix = LOG_PREFIX + "|deleteCampus|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            if (campus.isMain()) {
                throw new GlobalException(ApiCode.CONFLICT, "The main campus cannot be deleted");
            }
            if (classroomRepository.countByCampusId(campusId) > 0) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus already has classes and cannot be deleted");
            }
            if (roomRepository.countByCampusIdAndStatusNot(campusId, RoomStatus.DELETED) > 0) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus already has rooms and cannot be deleted");
            }
            if (campusRepository.countStaffAssignments(campusId) > 0) {
                throw new GlobalException(ApiCode.CONFLICT, "Campus already has staff and cannot be deleted");
            }
            try {
                campusRepository.delete(campus);
                campusRepository.flush();
            } catch (DataIntegrityViolationException integrityEx) {
                // Bảng khác (phòng, bếp...) còn tham chiếu điểm trường này
                throw new GlobalException(
                        ApiCode.CONFLICT, "Campus is used by other records and cannot be deleted", integrityEx);
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse changeCampusStatus(CurrentUser currentUser, Long campusId, CampusStatus status) {
        String logPrefix = LOG_PREFIX + "|changeCampusStatus|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            if (status == CampusStatus.INACTIVE && campus.isActive()) {
                if (campus.isMain()) {
                    throw new GlobalException(ApiCode.CONFLICT, "The main campus cannot be deactivated");
                }
                if (campusRepository.countOpenYearClasses(campusId) > 0) {
                    throw new GlobalException(
                            ApiCode.CONFLICT, "Campus has classes in the active or planned school year");
                }
                // Còn PHT phụ trách (hôm nay hoặc sắp tới) ⇒ Hiệu trưởng phải gỡ phân công trước (Cần chốt 22/10)
                List<String> vicePrincipals =
                        staffAssignmentRepository.findOpenVicePrincipalNames(campusId, LocalDate.now(clock));
                if (!vicePrincipals.isEmpty()) {
                    throw new GlobalException(
                            ApiCode.CONFLICT,
                            "Campus still has vice principals assigned: " + String.join(", ", vicePrincipals));
                }
                campus.deactivate(clock.instant());
            } else if (status == CampusStatus.ACTIVE && !campus.isActive()) {
                campus.activate();
            }
            campusRepository.flush();
            CampusResponse response = toResponseWithStats(campus);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean isPrincipal(CurrentUser currentUser, LocalDate today) {
        return currentUser.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(currentUser.userId(), today);
    }

    // AUTH-01: ghi cấu hình điểm trường chỉ Hiệu trưởng có phân công còn hiệu lực
    private void checkPrincipal(CurrentUser currentUser) {
        if (!isPrincipal(currentUser, LocalDate.now(clock))) {
            throw new GlobalException(ApiCode.FORBIDDEN, "Only the principal can manage campuses");
        }
    }

    private Set<Long> vicePrincipalCampusIds(CurrentUser currentUser, LocalDate today) {
        if (!currentUser.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            return Set.of();
        }
        return staffAssignmentService.findActiveAssignments(currentUser.userId(), today).stream()
                .filter(a -> a.staffRole() == StaffRole.VICE_PRINCIPAL && a.campusId() != null)
                .map(StaffAssignmentSummaryResponse::campusId)
                .collect(Collectors.toSet());
    }

    // Không truyền năm học ⇒ dùng năm đang hoạt động; chưa có năm nào ⇒ số liệu theo năm bằng 0
    // CFG-YR-02: năm đã kết thúc ⇒ thông tin điểm trường theo bản chụp lúc kết thúc năm; null = năm còn mở
    private Map<Long, SchoolYearRepository.CampusSnapshot> campusSnapshot(Long yearId) {
        if (yearId == null
                || schoolYearRepository
                        .findById(yearId)
                        .map(y -> y.getStatus() != SchoolYearStatus.CLOSED)
                        .orElse(true)) {
            return null;
        }
        return schoolYearRepository.findCampusSnapshot(yearId).stream()
                .collect(Collectors.toMap(SchoolYearRepository.CampusSnapshot::getCampusId, s -> s));
    }

    private static CampusResponse withSnapshot(
            CampusResponse r, Map<Long, SchoolYearRepository.CampusSnapshot> snapshot) {
        SchoolYearRepository.CampusSnapshot s = snapshot == null ? null : snapshot.get(r.id());
        if (s == null) {
            return r;
        }
        return new CampusResponse(
                r.id(),
                s.getCode(),
                s.getName(),
                s.getShortName(),
                s.getAddress(),
                s.getPhone(),
                Boolean.TRUE.equals(s.getMain()),
                // Năm đã kết thúc: điểm trường có trong bản chụp là đang hoạt động lúc đó
                CampusStatus.ACTIVE,
                r.classCount(),
                r.childCount(),
                r.vicePrincipals());
    }

    private Long resolveSchoolYearId(Long schoolYearId) {
        if (schoolYearId != null) {
            if (!schoolYearRepository.existsById(schoolYearId)) {
                throw new GlobalException(ApiCode.NOT_FOUND, "School year not found");
            }
            return schoolYearId;
        }
        return schoolYearRepository
                .findFirstByStatus(SchoolYearStatus.ACTIVE)
                .map(SchoolYear::getId)
                .orElse(null);
    }

    private Map<Long, List<StaffBriefResponse>> campusStaff(LocalDate today, StaffRole staffRole) {
        return campusRepository.findActiveCampusStaff(today).stream()
                .filter(s -> staffRole.name().equals(s.getStaffRole()))
                .collect(Collectors.groupingBy(
                        CampusRepository.CampusStaff::getCampusId,
                        Collectors.mapping(
                                s -> new StaffBriefResponse(s.getUserId(), s.getFullName()), Collectors.toList())));
    }

    private CampusResponse toResponseWithStats(Campus campus) {
        LocalDate today = LocalDate.now(clock);
        Long yearId = resolveSchoolYearId(null);
        long classCount = yearId == null
                ? 0
                : toMap(campusRepository.countClassesByCampus(yearId)).getOrDefault(campus.getId(), 0L);
        long childCount = yearId == null
                ? 0
                : toMap(campusRepository.countChildrenByCampus(yearId, today)).getOrDefault(campus.getId(), 0L);
        return campusMapper.toResponse(
                campus,
                classCount,
                childCount,
                campusStaff(today, StaffRole.VICE_PRINCIPAL).getOrDefault(campus.getId(), List.of()));
    }

    private void unsetCurrentMain() {
        campusRepository.findFirstByMainTrue().ifPresent(main -> {
            main.markMain(false);
            campusRepository.flush();
        });
    }

    private Campus findCampus(Long campusId) {
        return campusRepository
                .findById(campusId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Campus not found"));
    }

    private static Map<Long, Long> toMap(List<CampusRepository.CampusCount> counts) {
        return counts.stream()
                .collect(Collectors.toMap(
                        CampusRepository.CampusCount::getCampusId, CampusRepository.CampusCount::getTotal));
    }

    private static CampusRequest normalize(CampusRequest request) {
        String phone = request.phone() == null || request.phone().isBlank()
                ? null
                : request.phone().replaceAll("[\\s.\\-]", "").replaceFirst("^\\+?84", "0");
        return new CampusRequest(
                request.code().trim(),
                request.name().trim(),
                request.shortName().trim(),
                request.address().trim(),
                phone,
                request.isMain());
    }
}
